package com.devshawn.kafka.gitops.domain.state.settings;

import java.util.Optional;

public record SettingsFiles(Optional<String> services, Optional<String> topics, Optional<String> users) {
}
