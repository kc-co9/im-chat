package com.kim.omgchat.model.cqrs.command.user;

import lombok.Data;

/**
 * 用户登出command
 *
 * @author kc
 */
@Data
public class SignOutCommand {
    private Long userId;

    public SignOutCommand(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId is less than 0");
        }
        this.userId = userId;
    }
}
