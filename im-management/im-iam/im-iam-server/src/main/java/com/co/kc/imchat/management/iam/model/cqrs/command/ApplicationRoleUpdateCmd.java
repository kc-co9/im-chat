package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Set;

/** 更新 IAM 角色命令。 */
public record ApplicationRoleUpdateCmd(Long roleId, String name, Set<Long> permissionIds) {
    public ApplicationRoleUpdateCmd {
        AssertUtils.argNotNull("roleId must not be null", roleId);
        AssertUtils.argNotBlank("role name must not be blank", name);
        AssertUtils.argNotNull("role permission ids must not be null", permissionIds);
        permissionIds = Set.copyOf(permissionIds);
    }
}
