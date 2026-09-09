package com.co.kc.imchat.management.admin.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserUpdateCmd(Long userId, String username, String email) {
    public ManagedUserUpdateCmd {
        AssertUtils.argNotNull("user id must not be null", userId);
        AssertUtils.argNotBlank("username must not be blank", username);
        AssertUtils.argNotBlank("email must not be blank", email);
    }
}
