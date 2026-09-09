package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Set;

/** 修改 IAM 管理员角色命令。 */
public record AdministratorRoleChangeCmd(
        Long administratorId,
        Set<Long> roleIds
) {
    public AdministratorRoleChangeCmd {
        AssertUtils.allArgNotNull(
                "administrator role command must not contain null values",
                administratorId, roleIds);
        roleIds = Set.copyOf(roleIds);
    }
}
