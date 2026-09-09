package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 注册管理应用命令。 */
public record ApplicationRegisterCmd(
        String appKey,
        String name
) {
    public ApplicationRegisterCmd {
        AssertUtils.argNotBlank("appKey must not be blank", appKey);
        AssertUtils.argNotBlank("application name must not be blank", name);
    }
}
