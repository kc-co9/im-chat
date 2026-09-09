package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Set;

/** 创建应用角色命令。 */
public record ApplicationRoleCreateCmd(
        Long appId,
        String code,
        String name,
        Set<Long> permissionIds
) {
    public ApplicationRoleCreateCmd {
        AssertUtils.argNotNull("appId must not be null", appId);
        AssertUtils.argNotBlank("role code must not be blank", code);
        AssertUtils.argNotBlank("role name must not be blank", name);
        AssertUtils.argNotNull("role permission ids must not be null", permissionIds);
        permissionIds = Set.copyOf(permissionIds);
    }
}
