package com.co.kc.imchat.management.admin.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserGetQuery(Long userId) {
    public ManagedUserGetQuery {
        AssertUtils.argNotNull("user id must not be null", userId);
    }
}
