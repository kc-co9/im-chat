package com.co.kc.imchat.management.iam.sdk.catalog;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 由管理应用定义并全量注册到 IAM 的权限项。 */
public record IamPermissionDefinition(
        String code,
        String name,
        String description
) {
    public IamPermissionDefinition {
        AssertUtils.argNotBlank("IAM permission code must not be blank", code);
        AssertUtils.argNotBlank("IAM permission name must not be blank", name);
        AssertUtils.argNotBlank("IAM permission description must not be blank", description);
    }
}
