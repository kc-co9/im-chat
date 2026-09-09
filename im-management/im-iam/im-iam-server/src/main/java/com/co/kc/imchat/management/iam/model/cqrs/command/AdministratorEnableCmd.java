package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 启用 IAM 管理员命令。 */
public record AdministratorEnableCmd(Long administratorId) {
    public AdministratorEnableCmd {
        AssertUtils.argNotNull("administrator id must not be null", administratorId);
    }
}
