package com.co.kc.imchat.service.account.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 用户登出command
 *
 * @author kc
 */
public record UserSignOutCmd(
        /* 用户ID */
        Long userId
) {
    public UserSignOutCmd {
        AssertUtils.argNotNull("用户ID不能为空", userId);
        AssertUtils.argTrue("用户ID必须大于0", userId > 0);
    }
}
