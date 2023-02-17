package com.devshawn.kafka.gitops.domain.state;

import java.util.Map;
import java.util.Optional;

public record TopicDetails(Integer partitions, Optional<Integer> replication, Map<String, String> configs) {
}
