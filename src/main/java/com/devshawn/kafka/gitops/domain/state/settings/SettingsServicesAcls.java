package com.devshawn.kafka.gitops.domain.state.settings;

import java.util.Optional;

public record SettingsServicesAcls(Optional<Boolean> describeTopicEnabled) {
}