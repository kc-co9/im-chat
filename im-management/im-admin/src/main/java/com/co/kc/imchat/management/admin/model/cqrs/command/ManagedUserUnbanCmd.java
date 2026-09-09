package com.co.kc.imchat.management.admin.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserUnbanCmd(Long userId) {
    public ManagedUserUnbanCmd {
        AssertUtils.argNotNull("user id must not be null", userId);
    }
}
