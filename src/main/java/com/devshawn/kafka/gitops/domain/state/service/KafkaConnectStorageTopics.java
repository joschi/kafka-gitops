package com.devshawn.kafka.gitops.domain.state.service;

import java.util.Optional;

public record KafkaConnectStorageTopics(Optional<String> config,
                                        Optional<String> offset,
                                        Optional<String> status) {
}
