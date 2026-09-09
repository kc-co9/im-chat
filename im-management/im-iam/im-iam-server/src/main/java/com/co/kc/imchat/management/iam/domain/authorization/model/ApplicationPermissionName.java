package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 权限显示名称。 */
public record ApplicationPermissionName(String value) {
    public ApplicationPermissionName {
        AssertUtils.domainPropNotBlank("permission name must not be blank", value);
    }
}
