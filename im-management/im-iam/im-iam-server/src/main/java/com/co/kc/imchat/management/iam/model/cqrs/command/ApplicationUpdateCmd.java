package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;

/** 更新 IAM 接入应用命令；应用业务标识不可修改。 */
public record ApplicationUpdateCmd(
        Long appId,
        String name,
        AppStatus status
) {
    public ApplicationUpdateCmd {
        AssertUtils.argNotNull("appId must not be null", appId);
        AssertUtils.argNotBlank("application name must not be blank", name);
        AssertUtils.argNotNull("application status must not be null", status);
    }
}
