package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 删除 IAM 管理员命令。 */
public record AdministratorDeleteCmd(Long administratorId) {
    public AdministratorDeleteCmd {
        AssertUtils.argNotNull("administrator id must not be null", administratorId);
    }
}
