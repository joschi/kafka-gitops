package com.devshawn.kafka.gitops.domain.state.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public record SettingsTopics(
        Optional<SettingsTopicsDefaults> defaults,
        @JsonProperty("exclude") Optional<SettingsTopicsList> excludeList,
        @JsonProperty("include") Optional<SettingsTopicsList> includeList) {
}