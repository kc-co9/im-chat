package com.co.kc.imchat.application.model.cqrs.command.user;

import org.apache.commons.lang3.StringUtils;

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
        if (StringUtils.isBlank(email)) {
            throw new IllegalArgumentException("帐号为空");
        }
        if (StringUtils.isBlank(password)) {
            throw new IllegalArgumentException("密码为空");
        }
    }
}
