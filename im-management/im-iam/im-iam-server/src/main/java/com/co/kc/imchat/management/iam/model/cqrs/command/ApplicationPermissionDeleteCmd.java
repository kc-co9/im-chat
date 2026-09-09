package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 删除已停用权限命令。 */
public record ApplicationPermissionDeleteCmd(Long permissionId) {
    public ApplicationPermissionDeleteCmd {
        AssertUtils.argNotNull("permission id must not be null", permissionId);
    }
}
