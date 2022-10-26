package com.devshawn.kafka.gitops.config

import com.devshawn.kafka.gitops.exception.MissingConfigurationException
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.junit.ClassRule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class KafkaGitopsConfigLoaderSpec extends Specification {

    @Shared
    @ClassRule
    EnvironmentVariables environmentVariables

    void setupSpec() {
        environmentVariables.set("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        environmentVariables.set("KAFKA_SASL_MECHANISM", "PLAIN")
        environmentVariables.set("KAFKA_SECURITY_PROTOCOL", "SASL_PLAINTEXT")
    }

    @Unroll
    void 'test username and password shortcut with #saslMechanism'() {
        setup:
        environmentVariables.clear("KAFKA_SASL_MECHANISM")
        environmentVariables.set("KAFKA_SASL_MECHANISM", saslMechanism)
        environmentVariables.set("KAFKA_SASL_JAAS_USERNAME", "test")
        environmentVariables.set("KAFKA_SASL_JAAS_PASSWORD", "test-secret")

        when:
        KafkaGitopsConfig config = KafkaGitopsConfigLoader.load()

        then:
        config
        config.config.get(SaslConfigs.SASL_JAAS_CONFIG) == saslLoginModule + " required username=\"test\" password=\"test-secret\";"

        cleanup:
        environmentVariables.set("KAFKA_SASL_MECHANISM", "PLAIN")

        where:
        saslMechanism   | saslLoginModule
        "PLAIN"         | "org.apache.kafka.common.security.plain.PlainLoginModule"
        "SCRAM-SHA-256" | "org.apache.kafka.common.security.scram.ScramLoginModule"
        "SCRAM-SHA-512" | "org.apache.kafka.common.security.scram.ScramLoginModule"
    }

    void 'test escaping username and password shortcut'() {
        setup:
        environmentVariables.set("KAFKA_SASL_JAAS_USERNAME", "te\"st")
        environmentVariables.set("KAFKA_SASL_JAAS_PASSWORD", "te\"st-secr\"et")

        when:
        KafkaGitopsConfig config = KafkaGitopsConfigLoader.load()

        then:
        config
        config.config.get(SaslConfigs.SASL_JAAS_CONFIG) == "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"te\\\"st\" password=\"te\\\"st-secr\\\"et\";"
    }

    void 'test command config file'() {
        setup:
        File configFile = new File(getClass().getResource("/command.properties").toURI())
        environmentVariables.clear("KAFKA_BOOTSTRAP_SERVERS")

        when:
        KafkaGitopsConfig config = KafkaGitopsConfigLoader.load(configFile)

        then:
        config.config.get(CommonClientConfigs.CLIENT_ID_CONFIG) == "kafka-client-id"
        config.config.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG) == "example.com:9092"
        config.config.get(SaslConfigs.SASL_MECHANISM) == "PLAIN"
    }

    void 'load() fails with MissingConfigurationException if SASL mechanism is missing'() {
        setup:
        environmentVariables.set("KAFKA_SASL_JAAS_USERNAME", "test")
        environmentVariables.set("KAFKA_SASL_JAAS_PASSWORD", "test-secret")
        environmentVariables.clear("KAFKA_SASL_MECHANISM")

        when:
        KafkaGitopsConfigLoader.load()

        then:
        thrown(MissingConfigurationException)
    }

    void 'load() fails with MissingConfigurationException if username is missing'() {
        setup:
        environmentVariables.clear("KAFKA_SASL_JAAS_USERNAME")
        environmentVariables.set("KAFKA_SASL_JAAS_PASSWORD", "test-secret")

        when:
        KafkaGitopsConfigLoader.load()

        then:
        thrown(MissingConfigurationException)
    }

    void 'load() fails with MissingConfigurationException if password is missing'() {
        setup:
        environmentVariables.set("KAFKA_SASL_JAAS_USERNAME", "test")
        environmentVariables.clear("KAFKA_SASL_JAAS_PASSWORD")

        when:
        KafkaGitopsConfigLoader.load()

        then:
        thrown(MissingConfigurationException)
    }
}
