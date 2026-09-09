package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Set;

/** 替换管理员在指定应用中的角色分配。 */
public record ApplicationRoleAssignmentChangeCmd(
        Long appId,
        Long administratorId,
        Set<Long> roleIds
) {
    public ApplicationRoleAssignmentChangeCmd {
        AssertUtils.allArgNotNull(
                "application role assignment command must not contain null values",
                appId,
                administratorId,
                roleIds);
        roleIds = Set.copyOf(roleIds);
    }
}
