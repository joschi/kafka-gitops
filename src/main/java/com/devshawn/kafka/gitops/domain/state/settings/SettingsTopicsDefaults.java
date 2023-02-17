package com.devshawn.kafka.gitops.domain.state.settings;

import java.util.Optional;

public record SettingsTopicsDefaults(Optional<Integer> replication) {
}