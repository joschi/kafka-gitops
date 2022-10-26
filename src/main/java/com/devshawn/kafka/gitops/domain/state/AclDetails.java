package com.devshawn.kafka.gitops.domain.state;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.inferred.freebuilder.FreeBuilder;

@FreeBuilder
@JsonDeserialize(builder = AclDetails.Builder.class)
public interface AclDetails {

    String getName();

    String getType();

    String getPattern();

    String getPrincipal();

    String getHost();

    String getOperation();

    String getPermission();

    static AclDetails fromAclBinding(AclBinding aclBinding) {
        AclDetails.Builder aclDetails = new AclDetails.Builder()
                .setName(aclBinding.pattern().name())
                .setType(aclBinding.pattern().resourceType().name())
                .setPattern(aclBinding.pattern().patternType().name())
                .setPrincipal(aclBinding.entry().principal())
                .setHost(aclBinding.entry().host())
                .setOperation(aclBinding.entry().operation().name())
                .setPermission(aclBinding.entry().permissionType().name());
        return aclDetails.build();
    }

    static AclDetails.Builder fromCustomAclDetails(CustomAclDetails customAclDetails) {
        return new AclDetails.Builder()
                .setName(customAclDetails.getName())
                .setType(customAclDetails.getType())
                .setPattern(customAclDetails.getPattern())
                .setHost(customAclDetails.getHost())
                .setOperation(customAclDetails.getOperation())
                .setPermission(customAclDetails.getPermission());
    }

    default boolean equalsAclBinding(AclBinding aclBinding) {
        return aclBinding.pattern().name().equals(getName())
                && aclBinding.pattern().patternType().name().equals(getPattern())
                && aclBinding.pattern().resourceType().name().equals(getType())
                && aclBinding.entry().principal().equals(getPrincipal())
                && aclBinding.entry().host().equals(getHost())
                && aclBinding.entry().permissionType().name().equals(getPermission())
                && aclBinding.entry().operation().name().equals(getOperation());
    }

    default AclBinding toAclBinding() {
        return new AclBinding(
                new ResourcePattern(ResourceType.valueOf(getType()), getName(), PatternType.valueOf(getPattern())),
                new AccessControlEntry(getPrincipal(), getHost(), AclOperation.valueOf(getOperation()), AclPermissionType.valueOf(getPermission()))
        );
    }

    class Builder extends AclDetails_Builder {
        public Builder() {
            setPermission(AclPermissionType.ALLOW.name());
            setHost("*");
        }
    }
}
