package com.devshawn.kafka.gitops.domain.confluent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.inferred.freebuilder.FreeBuilder;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceAccount(String id, String name) {
}