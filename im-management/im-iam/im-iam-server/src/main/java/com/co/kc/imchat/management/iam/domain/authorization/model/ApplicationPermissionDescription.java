package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 权限用途说明。 */
public record ApplicationPermissionDescription(String value) {
    public ApplicationPermissionDescription {
        AssertUtils.domainPropNotBlank("permission description must not be blank", value);
    }
}
