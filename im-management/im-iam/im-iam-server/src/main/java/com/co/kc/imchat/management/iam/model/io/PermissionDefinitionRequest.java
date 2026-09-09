package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 权限目录项请求。 */
public record PermissionDefinitionRequest(
        String code,
        String name,
        String description
) {
    public PermissionDefinitionRequest {
        AssertUtils.argNotBlank("permission code must not be blank", code);
        AssertUtils.argNotBlank("permission name must not be blank", name);
        AssertUtils.argNotBlank("permission description must not be blank", description);
    }
}
