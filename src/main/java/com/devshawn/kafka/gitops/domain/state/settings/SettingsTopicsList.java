package com.devshawn.kafka.gitops.domain.state.settings;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.inferred.freebuilder.FreeBuilder;

import java.util.List;

@FreeBuilder
@JsonDeserialize(builder = SettingsTopicsList.Builder.class)
public interface SettingsTopicsList {

    List<String> getPrefixed();

    class Builder extends SettingsTopicsList_Builder {
    }
}
