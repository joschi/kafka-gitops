package com.devshawn.kafka.gitops.domain.state.settings;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.inferred.freebuilder.FreeBuilder;

import java.util.Optional;

@FreeBuilder
@JsonDeserialize(builder = SettingsTopics.Builder.class)
public interface SettingsTopics {

    Optional<SettingsTopicsDefaults> getDefaults();

    Optional<SettingsTopicsList> getBlacklist();

    Optional<SettingsTopicsList> getWhitelist();

    class Builder extends SettingsTopics_Builder {
    }
}
