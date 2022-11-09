package com.devshawn.kafka.gitops;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.devshawn.kafka.gitops.config.KafkaGitopsConfig;
import com.devshawn.kafka.gitops.config.KafkaGitopsConfigLoader;
import com.devshawn.kafka.gitops.config.ManagerConfig;
import com.devshawn.kafka.gitops.domain.confluent.ServiceAccount;
import com.devshawn.kafka.gitops.domain.options.GetAclOptions;
import com.devshawn.kafka.gitops.domain.plan.DesiredPlan;
import com.devshawn.kafka.gitops.domain.state.AclDetails;
import com.devshawn.kafka.gitops.domain.state.CustomAclDetails;
import com.devshawn.kafka.gitops.domain.state.DesiredState;
import com.devshawn.kafka.gitops.domain.state.DesiredStateFile;
import com.devshawn.kafka.gitops.domain.state.ServiceDetails;
import com.devshawn.kafka.gitops.domain.state.TopicDetails;
import com.devshawn.kafka.gitops.domain.state.service.KafkaStreamsService;
import com.devshawn.kafka.gitops.domain.state.settings.Settings;
import com.devshawn.kafka.gitops.domain.state.settings.SettingsCCloud;
import com.devshawn.kafka.gitops.domain.state.settings.SettingsTopics;
import com.devshawn.kafka.gitops.domain.state.settings.SettingsTopicsList;
import com.devshawn.kafka.gitops.exception.ConfluentCloudException;
import com.devshawn.kafka.gitops.exception.InvalidAclDefinitionException;
import com.devshawn.kafka.gitops.exception.MissingConfigurationException;
import com.devshawn.kafka.gitops.exception.ServiceAccountNotFoundException;
import com.devshawn.kafka.gitops.exception.ValidationException;
import com.devshawn.kafka.gitops.manager.ApplyManager;
import com.devshawn.kafka.gitops.manager.PlanManager;
import com.devshawn.kafka.gitops.service.ConfluentCloudService;
import com.devshawn.kafka.gitops.service.KafkaService;
import com.devshawn.kafka.gitops.service.ParserService;
import com.devshawn.kafka.gitops.service.RoleService;
import com.devshawn.kafka.gitops.util.LogUtil;
import com.devshawn.kafka.gitops.util.StateUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class StateManager {

    private final ManagerConfig managerConfig;
    private final ParserService parserService;
    private final RoleService roleService;
    private final ConfluentCloudService confluentCloudService;

    private final PlanManager planManager;
    private final ApplyManager applyManager;

    private boolean describeAclEnabled = false;

    public StateManager(ManagerConfig managerConfig, ParserService parserService) {
        initializeLogger(managerConfig.isVerboseRequested());
        ObjectMapper objectMapper = initializeObjectMapper();
        KafkaGitopsConfig config = KafkaGitopsConfigLoader.load();
        KafkaService kafkaService = new KafkaService(config);

        this.managerConfig = managerConfig;
        this.parserService = parserService;
        this.roleService = new RoleService();
        this.confluentCloudService = new ConfluentCloudService(objectMapper);
        this.planManager = new PlanManager(managerConfig, kafkaService, objectMapper);
        this.applyManager = new ApplyManager(managerConfig, kafkaService);
    }

    public DesiredStateFile getAndValidateStateFile() {
        DesiredStateFile desiredStateFile = parserService.parseStateFile();
        validateTopics(desiredStateFile);
        validateCustomAcls(desiredStateFile);
        this.describeAclEnabled = StateUtil.isDescribeTopicAclEnabled(desiredStateFile);
        return desiredStateFile;
    }

    public DesiredPlan plan() {
        DesiredPlan desiredPlan = generatePlan();
        planManager.writePlanToFile(desiredPlan);
        planManager.validatePlanHasChanges(desiredPlan,
                managerConfig.isDeleteDisabled(),
                managerConfig.isSkipAclsDisabled(),
                managerConfig.isSkipTopicsDisabled());
        return desiredPlan;
    }

    private DesiredPlan generatePlan() {
        DesiredState desiredState = getDesiredState();
        DesiredPlan.Builder desiredPlan = new DesiredPlan.Builder();
        if (!managerConfig.isSkipAclsDisabled()) {
            planManager.planAcls(desiredState, desiredPlan);
        }

        if (!managerConfig.isSkipTopicsDisabled()) {
            planManager.planTopics(desiredState, desiredPlan);
        }

        return desiredPlan.build();
    }

    public DesiredPlan apply() {
        DesiredPlan desiredPlan = planManager.readPlanFromFile();
        if (desiredPlan == null) {
            desiredPlan = generatePlan();
        }

        planManager.validatePlanHasChanges(desiredPlan, managerConfig.isDeleteDisabled(),
                managerConfig.isSkipAclsDisabled(), managerConfig.isSkipTopicsDisabled());

        if (!managerConfig.isSkipTopicsDisabled()) {
            applyManager.applyTopics(desiredPlan);
        }

        if (!managerConfig.isSkipAclsDisabled()) {
            applyManager.applyAcls(desiredPlan);
        }

        return desiredPlan;
    }

    public void createServiceAccounts() {
        DesiredStateFile desiredStateFile = parserService.parseStateFile();
        List<ServiceAccount> serviceAccounts = confluentCloudService.getServiceAccounts();
        AtomicInteger count = new AtomicInteger();
        if (isConfluentCloudEnabled(desiredStateFile)) {
            desiredStateFile.getServices().forEach((name, service) ->
                    createServiceAccount(name, serviceAccounts, count, false));

            desiredStateFile.getUsers().forEach((name, user) ->
                    createServiceAccount(name, serviceAccounts, count, true));
        } else {
            throw new ConfluentCloudException("Confluent Cloud must be enabled in the state file to use this command.");
        }

        if (count.get() == 0) {
            LogUtil.printSimpleSuccess("No service accounts were created as there are no new service accounts.");
        }
    }

    private void createServiceAccount(String name, List<ServiceAccount> serviceAccounts, AtomicInteger count, boolean isUser) {
        String fullName = isUser ? String.format("user-%s", name) : name;
        if (serviceAccounts.stream().noneMatch(it -> it.getName().equals(fullName))) {
            ServiceAccount serviceAccount = confluentCloudService.createServiceAccount(name, isUser);
            LogUtil.printSimpleSuccess(String.format("Successfully created service account: %s", serviceAccount.getName()));
            count.getAndIncrement();
        }
    }

    private DesiredState getDesiredState() {
        DesiredStateFile desiredStateFile = getAndValidateStateFile();
        DesiredState.Builder desiredState = new DesiredState.Builder()
                .addAllPrefixedTopicsToIgnore(getPrefixedTopicsToIgnore(desiredStateFile))
                .addAllPrefixedTopicsToAccept(getPrefixedTopicsToAccept(desiredStateFile));

        generateTopicsState(desiredState, desiredStateFile);

        if (isConfluentCloudEnabled(desiredStateFile)) {
            generateConfluentCloudServiceAcls(desiredState, desiredStateFile);
            generateConfluentCloudUserAcls(desiredState, desiredStateFile);
        } else {
            generateServiceAcls(desiredState, desiredStateFile);
            generateUserAcls(desiredState, desiredStateFile);
        }

        return desiredState.build();
    }

    private void generateTopicsState(DesiredState.Builder desiredState, DesiredStateFile desiredStateFile) {
        Optional<Integer> defaultReplication = StateUtil.fetchReplication(desiredStateFile);
        if (defaultReplication.isPresent()) {
            desiredStateFile.getTopics().forEach((name, details) -> {
                Integer replication = details.getReplication().isPresent() ? details.getReplication().get() : defaultReplication.get();
                desiredState.putTopics(name, new TopicDetails.Builder().mergeFrom(details).setReplication(replication).build());
            });
        } else {
            desiredState.putAllTopics(desiredStateFile.getTopics());
        }
    }

    private void generateConfluentCloudServiceAcls(DesiredState.Builder desiredState, DesiredStateFile desiredStateFile) {
        List<ServiceAccount> serviceAccounts = confluentCloudService.getServiceAccounts();
        desiredStateFile.getServices().forEach((name, service) -> {
            String serviceAccountId = serviceAccounts.stream()
                    .filter(it -> it.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new ServiceAccountNotFoundException(name))
                    .getId();

            AtomicInteger index = new AtomicInteger();
            for (AclDetails.Builder builder : service.getAcls(buildGetAclOptions(name))) {
                builder.setPrincipal(principal(serviceAccountId));
                desiredState.putAcls(acl(name, index), builder.build());
            }

            if (desiredStateFile.getCustomServiceAcls().containsKey(name)) {
                Map<String, CustomAclDetails> customAcls = desiredStateFile.getCustomServiceAcls().get(name);
                customAcls.forEach((aclName, customAcl) -> {
                    AclDetails.Builder aclDetails = AclDetails.fromCustomAclDetails(customAcl);
                    aclDetails.setPrincipal(principal(serviceAccountId));
                    desiredState.putAcls(acl(name, index), aclDetails.build());
                });
            }
        });
    }

    private static String acl(String name, AtomicInteger index) {
        return String.format("%s-%s", name, index.getAndIncrement());
    }

    private static String principal(String serviceAccountId) {
        return String.format("User:%s", serviceAccountId);
    }

    private void generateConfluentCloudUserAcls(DesiredState.Builder desiredState, DesiredStateFile desiredStateFile) {
        List<ServiceAccount> serviceAccounts = confluentCloudService.getServiceAccounts();
        desiredStateFile.getUsers().forEach((name, user) -> {
            AtomicInteger index = new AtomicInteger();
            String serviceAccountName = String.format("user-%s", name);

            Optional<ServiceAccount> serviceAccount = serviceAccounts.stream().filter(it -> it.getName().equals(serviceAccountName)).findFirst();
            String serviceAccountId = serviceAccount.orElseThrow(() -> new ServiceAccountNotFoundException(serviceAccountName)).getId();

            user.getRoles().forEach(role -> {
                List<AclDetails.Builder> acls = roleService.getAcls(role, principal(serviceAccountId));
                acls.forEach(acl -> desiredState.putAcls(acl(name, index), acl.build()));
            });

            if (desiredStateFile.getCustomUserAcls().containsKey(name)) {
                Map<String, CustomAclDetails> customAcls = desiredStateFile.getCustomUserAcls().get(name);
                customAcls.forEach((aclName, customAcl) -> {
                    AclDetails.Builder aclDetails = AclDetails.fromCustomAclDetails(customAcl);
                    aclDetails.setPrincipal(principal(serviceAccountId));
                    desiredState.putAcls(acl(name, index), aclDetails.build());
                });
            }
        });
    }

    private void generateServiceAcls(DesiredState.Builder desiredState, DesiredStateFile desiredStateFile) {
        for (Map.Entry<String, ServiceDetails> entry : desiredStateFile.getServices().entrySet()) {
            String name = entry.getKey();
            ServiceDetails service = entry.getValue();
            AtomicInteger index = new AtomicInteger();

            service.getAcls(buildGetAclOptions(name)).forEach(aclDetails ->
                    desiredState.putAcls(acl(name, index), buildAclDetails(name, aclDetails)));

            if (desiredStateFile.getCustomServiceAcls().containsKey(name)) {
                Map<String, CustomAclDetails> customAcls = desiredStateFile.getCustomServiceAcls().get(name);
                customAcls.forEach((aclName, customAcl) -> {
                    AclDetails.Builder aclDetails = AclDetails.fromCustomAclDetails(customAcl);
                    aclDetails.setPrincipal(customAcl.getPrincipal().orElseThrow(() ->
                            new MissingConfigurationException(String.format("Missing principal for custom service ACL %s", aclName))));
                    desiredState.putAcls(acl(name, index), aclDetails.build());
                });
            }
        }
    }

    private void generateUserAcls(DesiredState.Builder desiredState, DesiredStateFile desiredStateFile) {
        desiredStateFile.getUsers().forEach((name, user) -> {
            AtomicInteger index = new AtomicInteger();
            String userPrincipal = user.getPrincipal()
                    .orElseThrow(() -> new MissingConfigurationException(String.format("Missing principal for user %s", name)));

            for (String role : user.getRoles()) {
                List<AclDetails.Builder> acls = roleService.getAcls(role, userPrincipal);
                for (AclDetails.Builder acl : acls) {
                    desiredState.putAcls(acl(name, index), acl.build());
                }
            }

            if (desiredStateFile.getCustomUserAcls().containsKey(name)) {
                Map<String, CustomAclDetails> customAcls = desiredStateFile.getCustomUserAcls().get(name);
                customAcls.forEach((aclName, customAcl) -> {
                    AclDetails.Builder aclDetails = AclDetails.fromCustomAclDetails(customAcl);
                    aclDetails.setPrincipal(customAcl.getPrincipal().orElse(userPrincipal));
                    desiredState.putAcls(acl(name, index), aclDetails.build());
                });
            }
        });
    }

    private AclDetails buildAclDetails(String service, AclDetails.Builder aclDetails) {
        try {
            return aclDetails.build();
        } catch (IllegalStateException ex) {
            throw new MissingConfigurationException(String.format("%s for service: %s", ex.getMessage(), service));
        }
    }

    private List<String> getPrefixedTopicsToIgnore(DesiredStateFile desiredStateFile) {
        List<String> topics = new ArrayList<>();
        desiredStateFile.getSettings()
                .flatMap(Settings::getTopics)
                .flatMap(SettingsTopics::getBlacklist)
                .map(SettingsTopicsList::getPrefixed)
                .ifPresent(topics::addAll);

        desiredStateFile.getServices().forEach((name, service) -> {
            if (service instanceof KafkaStreamsService) {
                topics.add(name);
            }
        });
        return topics;
    }

    private List<String> getPrefixedTopicsToAccept(DesiredStateFile desiredStateFile) {
        return desiredStateFile.getSettings()
                .flatMap(Settings::getTopics)
                .flatMap(SettingsTopics::getWhitelist)
                .map(SettingsTopicsList::getPrefixed)
                .stream()
                .flatMap(Collection::stream)
                .toList();
    }

    private GetAclOptions buildGetAclOptions(String serviceName) {
        return new GetAclOptions.Builder().setServiceName(serviceName).setDescribeAclEnabled(describeAclEnabled).build();
    }

    private void validateCustomAcls(DesiredStateFile desiredStateFile) {
        desiredStateFile.getCustomServiceAcls().forEach((service, details) -> {
            try {
                details.values().forEach(CustomAclDetails::validate);
            } catch (InvalidAclDefinitionException ex) {
                String message = String.format("Custom ACL definition for service '%s' is invalid for field '%s'. Allowed values: [%s]", service, ex.getField(), String.join(", ", ex.getAllowedValues()));
                throw new ValidationException(message);
            }
        });

        desiredStateFile.getCustomUserAcls().forEach((service, details) -> {
            try {
                details.values().forEach(CustomAclDetails::validate);
            } catch (InvalidAclDefinitionException ex) {
                String message = String.format("Custom ACL definition for user '%s' is invalid for field '%s'. Allowed values: [%s]", service, ex.getField(), String.join(", ", ex.getAllowedValues()));
                throw new ValidationException(message);
            }
        });
    }

    private void validateTopics(DesiredStateFile desiredStateFile) {
        Optional<Integer> defaultReplication = StateUtil.fetchReplication(desiredStateFile);
        if (defaultReplication.isEmpty()) {
            desiredStateFile.getTopics().forEach((name, details) -> {
                if (details.getReplication().isEmpty()) {
                    throw new ValidationException(String.format("Not set: [replication] in state file definition: topics -> %s", name));
                }
            });
        } else {
            if (defaultReplication.get() < 1) {
                throw new ValidationException("The default replication factor must be a positive integer.");
            }
        }
    }

    private boolean isConfluentCloudEnabled(DesiredStateFile desiredStateFile) {
        return desiredStateFile.getSettings()
                .flatMap(Settings::getCcloud)
                .map(SettingsCCloud::isEnabled)
                .orElse(false);
    }

    private static ObjectMapper initializeObjectMapper() {
        return new ObjectMapper()
        .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(new Jdk8Module());
    }

    private static void initializeLogger(boolean verbose) {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Logger kafka = (Logger) LoggerFactory.getLogger("org.apache.kafka");
        if (verbose) {
            root.setLevel(Level.INFO);
            kafka.setLevel(Level.WARN);
        } else {
            root.setLevel(Level.WARN);
            kafka.setLevel(Level.OFF);
        }
    }
}
