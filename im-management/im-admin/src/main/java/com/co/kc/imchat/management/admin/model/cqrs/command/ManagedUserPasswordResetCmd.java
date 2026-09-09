package com.co.kc.imchat.management.admin.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserPasswordResetCmd(Long userId, String password) {
    public ManagedUserPasswordResetCmd {
        AssertUtils.argNotNull("user id must not be null", userId);
        AssertUtils.argNotBlank("user password must not be blank", password);
    }
}
