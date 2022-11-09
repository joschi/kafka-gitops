package com.devshawn.kafka.gitops.manager;

import com.devshawn.kafka.gitops.config.ManagerConfig;
import com.devshawn.kafka.gitops.domain.plan.AclPlan;
import com.devshawn.kafka.gitops.domain.plan.DesiredPlan;
import com.devshawn.kafka.gitops.domain.plan.PlanOverview;
import com.devshawn.kafka.gitops.domain.plan.TopicConfigPlan;
import com.devshawn.kafka.gitops.domain.plan.TopicPlan;
import com.devshawn.kafka.gitops.domain.state.AclDetails;
import com.devshawn.kafka.gitops.domain.state.DesiredState;
import com.devshawn.kafka.gitops.domain.state.TopicDetails;
import com.devshawn.kafka.gitops.enums.PlanAction;
import com.devshawn.kafka.gitops.exception.PlanIsUpToDateException;
import com.devshawn.kafka.gitops.exception.ReadPlanInputException;
import com.devshawn.kafka.gitops.exception.WritePlanOutputException;
import com.devshawn.kafka.gitops.service.KafkaService;
import com.devshawn.kafka.gitops.util.PlanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanManager {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(PlanManager.class);

    private final ManagerConfig managerConfig;
    private final KafkaService kafkaService;
    private final ObjectMapper objectMapper;

    public PlanManager(ManagerConfig managerConfig, KafkaService kafkaService, ObjectMapper objectMapper) {
        this.managerConfig = managerConfig;
        this.kafkaService = kafkaService;
        this.objectMapper = objectMapper;
    }

    public void planTopics(DesiredState desiredState, DesiredPlan.Builder desiredPlan) {
        List<TopicListing> topics = kafkaService.getTopics();
        List<String> topicNames = topics.stream().map(TopicListing::name).toList();
        Map<String, List<ConfigEntry>> topicConfigs = fetchTopicConfigurations(topicNames);

        desiredState.getTopics().forEach((key, value) -> {
            TopicPlan.Builder topicPlan = new TopicPlan.Builder()
                    .setName(key)
                    .setTopicDetails(value);

            if (!topicNames.contains(key)) {
                LOG.info("[PLAN] Topic {} does not exist; it will be created.", key);
                topicPlan.setAction(PlanAction.ADD);
            } else {
                LOG.info("[PLAN] Topic {} exists, it will not be created.", key);
                topicPlan.setAction(PlanAction.NO_CHANGE);
                planTopicConfigurations(key, value, topicConfigs.get(key), topicPlan);
            }

            desiredPlan.addTopicPlans(topicPlan.build());
        });

        for (TopicListing currentTopic : topics) {
            boolean acceptTopic = desiredState.getPrefixedTopicsToAccept().stream().anyMatch(it -> currentTopic.name().startsWith(it));
            if (!desiredState.getPrefixedTopicsToAccept().isEmpty() && !acceptTopic) {
                LOG.info("[PLAN] Ignoring topic {} due to missing prefix (whitelist)", currentTopic.name());
                continue;
            }

            boolean ignoreTopic = desiredState.getPrefixedTopicsToIgnore().stream().anyMatch(it -> currentTopic.name().startsWith(it));
            if (ignoreTopic) {
                LOG.info("[PLAN] Ignoring topic {} due to prefix (blacklist)", currentTopic.name());
                continue;
            }

            if (!managerConfig.isDeleteDisabled() && !desiredState.getTopics().containsKey(currentTopic.name())) {
                TopicPlan topicPlan = new TopicPlan.Builder()
                        .setName(currentTopic.name())
                        .setAction(PlanAction.REMOVE)
                        .build();

                desiredPlan.addTopicPlans(topicPlan);
            }
        }
    }

    private void planTopicConfigurations(String topicName, TopicDetails topicDetails, List<ConfigEntry> configs, TopicPlan.Builder topicPlan) {
        Map<String, TopicConfigPlan> configPlans = new HashMap<>();
        List<ConfigEntry> customConfigs = configs.stream()
                .filter(it -> it.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG)
                .toList();

        customConfigs.forEach(currentConfig -> {
            String newConfig = topicDetails.getConfigs().getOrDefault(currentConfig.name(), null);

            TopicConfigPlan.Builder topicConfigPlan = new TopicConfigPlan.Builder()
                    .setKey(currentConfig.name());

            if (currentConfig.value().equals(newConfig)) {
                topicConfigPlan.setAction(PlanAction.NO_CHANGE);
                topicConfigPlan.setValue(currentConfig.value());
                configPlans.put(currentConfig.name(), topicConfigPlan.build());
            } else if (newConfig == null) {
                topicConfigPlan.setAction(PlanAction.REMOVE);
                configPlans.put(currentConfig.name(), topicConfigPlan.build());
                topicPlan.setAction(PlanAction.UPDATE);
            }
        });

        topicDetails.getConfigs().forEach((key, value) -> {
            ConfigEntry currentConfig = customConfigs.stream().filter(it -> it.name().equals(key)).findFirst().orElse(null);

            TopicConfigPlan.Builder topicConfigPlan = new TopicConfigPlan.Builder()
                    .setKey(key)
                    .setValue(value);

            if (currentConfig == null) {
                topicConfigPlan.setAction(PlanAction.ADD);
                configPlans.put(key, topicConfigPlan.build());
                topicPlan.setAction(PlanAction.UPDATE);
            } else if (!currentConfig.value().equals(value)) {
                topicConfigPlan.setAction(PlanAction.UPDATE);
                configPlans.put(key, topicConfigPlan.build());
                topicPlan.setAction(PlanAction.UPDATE);
            }
        });

        configPlans.forEach((key, plan) -> {
            LOG.info("[PLAN] Topic {} | [{}] {}", topicName, plan.getAction(), plan.getKey());
            topicPlan.addTopicConfigPlans(plan);
        });
    }

    public void planAcls(DesiredState desiredState, DesiredPlan.Builder desiredPlan) {
        List<AclBinding> currentAcls = kafkaService.getAcls();

        currentAcls.forEach(acl -> {
            Map.Entry<String, AclDetails> detailsEntry = desiredState.getAcls().entrySet().stream()
                    .filter(entry -> entry.getValue().equalsAclBinding(acl))
                    .findFirst().orElse(null);

            AclPlan.Builder aclPlan = new AclPlan.Builder();

            if (detailsEntry != null) {
                aclPlan.setName(detailsEntry.getKey());
                aclPlan.setAclDetails(detailsEntry.getValue());
                aclPlan.setAction(PlanAction.NO_CHANGE);
                desiredPlan.addAclPlans(aclPlan.build());
            } else {
                aclPlan.setName("Unnamed ACL");
                aclPlan.setAclDetails(AclDetails.fromAclBinding(acl));
                aclPlan.setAction(PlanAction.REMOVE);

                if (!managerConfig.isDeleteDisabled()) {
                    desiredPlan.addAclPlans(aclPlan.build());
                }
            }
        });

        desiredState.getAcls().forEach((key, value) -> {
            AclBinding aclBinding = currentAcls.stream().filter(value::equalsAclBinding).findFirst().orElse(null);
            if (aclBinding == null) {
                AclPlan aclPlan = new AclPlan.Builder()
                        .setName(key)
                        .setAclDetails(value)
                        .setAction(PlanAction.ADD)
                        .build();

                desiredPlan.addAclPlans(aclPlan);
            }
        });
    }

    public void validatePlanHasChanges(DesiredPlan desiredPlan,
                                       boolean deleteDisabled,
                                       boolean skipAclsDisabled,
                                       boolean skipTopicsDisabled) {
        PlanOverview planOverview = PlanUtil.getOverview(desiredPlan, deleteDisabled, skipAclsDisabled, skipTopicsDisabled);
        if (planOverview.getAdd() == 0 && planOverview.getUpdate() == 0 && planOverview.getRemove() == 0) {
            throw new PlanIsUpToDateException();
        }
    }

    public DesiredPlan readPlanFromFile() {
        return managerConfig.getPlanFile().map(file -> {
            try {
                return objectMapper.readValue(file, DesiredPlan.class);
            } catch (FileNotFoundException ex) {
                throw new ReadPlanInputException("The specified plan file could not be found.");
            } catch (IOException ex) {
                throw new ReadPlanInputException();
            }
        }).orElse(null);
    }

    public void writePlanToFile(DesiredPlan desiredPlan) {
        managerConfig.getPlanFile().ifPresent(planFile -> {
            try {
                if (!planFile.createNewFile()) {
                    LOG.info("Overwriting existing plan file at {}", planFile);
                }
                DesiredPlan outputPlan = managerConfig.isIncludeUnchangedEnabled() ? desiredPlan : desiredPlan.toChangesOnlyPlan();
                try(FileWriter writer = new FileWriter(planFile)) {
                    writer.write(objectMapper.writeValueAsString(outputPlan));
                }
            } catch (IOException ex) {
                throw new WritePlanOutputException(ex.getMessage());
            }
        });
    }

    private Map<String, List<ConfigEntry>> fetchTopicConfigurations(List<String> topicNames) {
        Map<String, List<ConfigEntry>> map = new HashMap<>();
        Map<ConfigResource, Config> configs = kafkaService.describeConfigsForTopics(topicNames);
        configs.forEach((key, value) -> map.put(key.name(), new ArrayList<>(value.entries())));
        return map;
    }
}
