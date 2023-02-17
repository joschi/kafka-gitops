package com.devshawn.kafka.gitops.domain.state.settings;

import java.util.Optional;

public record Settings(Optional<SettingsCCloud> ccloud,
                       Optional<SettingsTopics> topics,
                       Optional<SettingsServices> services,
                       Optional<SettingsFiles> files) {
}
