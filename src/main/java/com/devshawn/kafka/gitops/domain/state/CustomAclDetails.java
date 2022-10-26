package com.devshawn.kafka.gitops.domain.state;

import com.devshawn.kafka.gitops.exception.InvalidAclDefinitionException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourceType;
import org.inferred.freebuilder.FreeBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@FreeBuilder
@JsonDeserialize(builder = CustomAclDetails.Builder.class)
public interface CustomAclDetails {

    String getName();

    String getType();

    String getPattern();

    Optional<String> getPrincipal();

    String getHost();

    String getOperation();

    String getPermission();

    default void validate() {
        validateEnum(ResourceType.class, getType(), "type");
        validateEnum(PatternType.class, getPattern(), "pattern");
        validateEnum(AclOperation.class, getOperation(), "operation");
        validateEnum(AclPermissionType.class, getPermission(), "permission");
    }

    private <E extends Enum<E>> void validateEnum(Class<E> enumData, String value, String field) {
        List<String> allowedValues = Arrays.stream(enumData.getEnumConstants())
                .map(Enum::name)
                .filter(it -> !it.equals("ANY") && !it.equals("UNKNOWN"))
                .toList();
        if (!allowedValues.contains(value)) {
            throw new InvalidAclDefinitionException(field, value, allowedValues);
        }
    }

    class Builder extends CustomAclDetails_Builder {
        public Builder() {
            setPermission(AclPermissionType.ALLOW.name());
            setHost("*");
        }
    }
}