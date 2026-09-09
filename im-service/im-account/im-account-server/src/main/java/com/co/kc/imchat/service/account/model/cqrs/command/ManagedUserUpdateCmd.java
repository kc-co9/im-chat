package com.co.kc.imchat.service.account.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserUpdateCmd(Long userId, String username, String email) {
    public ManagedUserUpdateCmd {
        AssertUtils.argNotNull("userId must not be null", userId);
        AssertUtils.argTrue("userId must be positive", userId > 0L);
        AssertUtils.argNotBlank("username must not be blank", username);
        AssertUtils.argNotBlank("email must not be blank", email);
        username = username.trim();
        email = email.trim();
    }
}
