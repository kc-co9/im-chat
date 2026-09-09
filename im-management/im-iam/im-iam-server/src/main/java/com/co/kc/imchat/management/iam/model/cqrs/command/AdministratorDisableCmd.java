package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 停用 IAM 管理账号命令。 */
public record AdministratorDisableCmd(Long administratorId) {
    public AdministratorDisableCmd {
        AssertUtils.argNotNull("administratorId must not be null", administratorId);
    }
}
