package com.co.kc.imchat.service.account.model.cqrs.command;

public record UserSignUpCmd(
        /* 邮箱 */
        String email,
        /* 用户名 */
        String username,
        /* 密码 */
        String password
) {
}
