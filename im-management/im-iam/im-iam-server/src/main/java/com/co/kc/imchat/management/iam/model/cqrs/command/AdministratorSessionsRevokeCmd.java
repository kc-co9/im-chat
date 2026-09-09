package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 撤销 IAM 管理员全部应用授权命令。 */
public record AdministratorSessionsRevokeCmd(Long administratorId) {
    public AdministratorSessionsRevokeCmd {
        AssertUtils.argNotNull("administrator id must not be null", administratorId);
    }
}
