package com.devshawn.kafka.gitops.domain.plan;

import com.devshawn.kafka.gitops.enums.PlanAction;

import java.util.Optional;

public record TopicConfigPlan(String key, Optional<String> value, PlanAction action) {
}
