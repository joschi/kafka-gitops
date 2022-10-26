package com.devshawn.kafka.gitops.config;

import com.devshawn.kafka.gitops.exception.MissingConfigurationException;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class KafkaGitopsConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaGitopsConfigLoader.class);

    private KafkaGitopsConfigLoader() {
    }

    public static KafkaGitopsConfig load() {
        return load(null);
    }

    public static KafkaGitopsConfig load(File configFile) {
        KafkaGitopsConfig.Builder builder = new KafkaGitopsConfig.Builder();
        setConfigFromFile(configFile, builder);
        setConfigFromEnvironment(builder);
        return builder.build();
    }

    private static void setConfigFromFile(File configFile, KafkaGitopsConfig.Builder builder) {
        if (configFile == null) {
            return;
        }
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Properties properties = new Properties();
            properties.load(inputStream);
            properties.forEach( (k, v) -> builder.putConfig(k.toString(), v));
        } catch (IOException ioExc) {
            LOG.error("Failed to load config from " + configFile, ioExc);
        }
    }

    private static void setConfigFromEnvironment(KafkaGitopsConfig.Builder builder) {
        Map<String, Object> config = new HashMap<>();
        String username = null;
        String password = null;

        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.equals("KAFKA_SASL_JAAS_USERNAME")) {
                username = value;
            } else if (key.equals("KAFKA_SASL_JAAS_PASSWORD")) {
                password = value;
            } else if (key.startsWith("KAFKA_")) {
                String newKey = key.substring(6).replace("_", ".").toLowerCase(Locale.ROOT);
                config.put(newKey, value);
            }
        }

        config.putAll(handleAuthentication(config.get(SaslConfigs.SASL_MECHANISM), username, password));

        LOG.info("Kafka Config: {}", sanitizeConfiguration(config));

        builder.putAllConfig(config);
        handleDefaultConfig(builder);
    }

    private static Map<String, Object> sanitizeConfiguration(Map<String, Object> config) {
        Map<String, Object> sanitizedConfig = new HashMap<>(config);

        String saslConfig = (String) config.get(SaslConfigs.SASL_JAAS_CONFIG);
        if (saslConfig != null) {
            sanitizedConfig.replace(SaslConfigs.SASL_JAAS_CONFIG, saslConfig.replaceFirst("password=\".*\";", "password=[redacted];"));
        }

        return sanitizedConfig;
    }

    private static void handleDefaultConfig(KafkaGitopsConfig.Builder builder) {
        if (!builder.getConfig().containsKey(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)) {
            builder.putConfig(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        }

        if (!builder.getConfig().containsKey(CommonClientConfigs.CLIENT_ID_CONFIG)) {
            builder.putConfig(CommonClientConfigs.CLIENT_ID_CONFIG, "kafka-gitops");
        }
    }

    private static Map<String, Object> handleAuthentication(Object saslMechanism, String username, String password) {
        if (username != null && password != null) {
            // Do we need the Plain or SCRAM module?
            String loginModule;
            if ("PLAIN".equals(saslMechanism)) {
                loginModule = "org.apache.kafka.common.security.plain.PlainLoginModule";
            } else if ("SCRAM-SHA-256".equals(saslMechanism) || "SCRAM-SHA-512".equals(saslMechanism)) {
                loginModule = "org.apache.kafka.common.security.scram.ScramLoginModule";
            } else {
                throw new MissingConfigurationException("KAFKA_SASL_MECHANISM");
            }

            String value = String.format("%s required username=\"%s\" password=\"%s\";",
                    loginModule, escape(username), escape(password));
            return Map.of(SaslConfigs.SASL_JAAS_CONFIG, value);
        } else if (username != null) {
            throw new MissingConfigurationException("KAFKA_SASL_JAAS_PASSWORD");
        } else if (password != null) {
            throw new MissingConfigurationException("KAFKA_SASL_JAAS_USERNAME");
        }

        return Collections.emptyMap();
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
