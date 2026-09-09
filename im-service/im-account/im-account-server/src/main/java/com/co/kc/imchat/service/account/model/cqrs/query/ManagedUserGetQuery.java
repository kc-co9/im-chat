package com.co.kc.imchat.service.account.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserGetQuery(Long userId) {
    public ManagedUserGetQuery {
        AssertUtils.argNotNull("userId must not be null", userId);
        AssertUtils.argTrue("userId must be positive", userId > 0L);
    }
}
