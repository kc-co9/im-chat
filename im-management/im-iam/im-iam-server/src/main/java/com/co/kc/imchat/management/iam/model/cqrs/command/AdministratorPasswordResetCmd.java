package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 重置 IAM 管理员密码命令。 */
public record AdministratorPasswordResetCmd(Long administratorId, String password) {
    public AdministratorPasswordResetCmd {
        AssertUtils.argNotNull("administrator id must not be null", administratorId);
        AssertUtils.argNotBlank("administrator password must not be blank", password);
    }
}
