package com.co.kc.imchat.service.account.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserDeleteCmd(Long userId) {
    public ManagedUserDeleteCmd {
        AssertUtils.argNotNull("userId must not be null", userId);
        AssertUtils.argTrue("userId must be positive", userId > 0L);
    }
}
