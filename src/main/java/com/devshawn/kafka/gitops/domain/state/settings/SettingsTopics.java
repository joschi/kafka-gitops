package com.devshawn.kafka.gitops.domain.state.settings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.inferred.freebuilder.FreeBuilder;

import java.util.Optional;

@FreeBuilder
@JsonDeserialize(builder = SettingsTopics.Builder.class)
public interface SettingsTopics {

    Optional<SettingsTopicsDefaults> getDefaults();

    @JsonProperty("exclude")
    Optional<SettingsTopicsList> getExcludeList();

    @JsonProperty("include")
    Optional<SettingsTopicsList> getIncludeList();

    class Builder extends SettingsTopics_Builder {
    }
}
