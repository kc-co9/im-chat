package com.co.kc.imchat.management.admin.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserBanCmd(Long userId) {
    public ManagedUserBanCmd {
        AssertUtils.argNotNull("user id must not be null", userId);
    }
}
