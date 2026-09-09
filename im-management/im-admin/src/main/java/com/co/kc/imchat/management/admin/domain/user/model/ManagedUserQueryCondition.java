package com.co.kc.imchat.management.admin.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Optional;

public record ManagedUserQueryCondition(
        Optional<ManagedUserId> userId,
        Optional<ManagedUserName> username,
        Optional<ManagedUserEmail> email,
        Optional<ManagedUserStatus> status
) {
    public ManagedUserQueryCondition {
        AssertUtils.allArgNotNull(
                "managed user query condition options must not be null",
                userId, username, email, status);
    }
}
