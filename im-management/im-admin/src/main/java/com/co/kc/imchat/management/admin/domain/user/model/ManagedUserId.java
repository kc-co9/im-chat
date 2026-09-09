package com.co.kc.imchat.management.admin.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserId(long value) {
    public ManagedUserId {
        AssertUtils.domainPropTrue("managed user id must be positive", value > 0);
    }
}
