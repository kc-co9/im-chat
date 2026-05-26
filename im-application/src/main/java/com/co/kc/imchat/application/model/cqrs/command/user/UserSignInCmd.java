package com.co.kc.imchat.application.model.cqrs.command.user;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 用户认证command
 *
 * @author kc
 */
public record UserSignInCmd(
        /* 邮箱 */
        String email,
        /* 密码 */
        String password
) {
    public UserSignInCmd {
        AssertUtils.argNotBlank("帐号为空", email);
        AssertUtils.argNotBlank("密码为空", password);
    }
}
