package com.devshawn.kafka.gitops.domain.state.settings;

import java.util.Optional;

public record SettingsServices(Optional<SettingsServicesAcls> acls) {
}