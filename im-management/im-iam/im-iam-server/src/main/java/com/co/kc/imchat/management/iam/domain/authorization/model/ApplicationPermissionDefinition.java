package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 权限目录中的一项定义。 */
public record ApplicationPermissionDefinition(
        ApplicationPermissionCode code,
        ApplicationPermissionName name,
        ApplicationPermissionDescription description
) {
    public ApplicationPermissionDefinition {
        AssertUtils.allDomainPropNotNull(
                "permission definition must not contain null values",
                code, name, description);
    }
}
