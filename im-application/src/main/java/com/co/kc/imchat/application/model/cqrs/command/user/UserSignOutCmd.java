package com.co.kc.imchat.application.model.cqrs.command.user;

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
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId is less than 0");
        }
    }
}
