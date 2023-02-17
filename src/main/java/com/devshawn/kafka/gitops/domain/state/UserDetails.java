package com.devshawn.kafka.gitops.domain.state;

import java.util.List;
import java.util.Optional;

public record UserDetails(Optional<String> principal, List<String> roles) {
}
