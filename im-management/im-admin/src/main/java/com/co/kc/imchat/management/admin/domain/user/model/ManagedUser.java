package com.co.kc.imchat.management.admin.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

public record ManagedUser(ManagedUserId id,
                          ManagedUserName username,
                          ManagedUserEmail email,
                          ManagedUserStatus status,
                          Boolean deleted,
                          Instant createdAt,
                          Instant updatedAt) {
    public ManagedUser {
        AssertUtils.domainPropNotNull("managed user id must not be null", id);
        AssertUtils.domainPropNotNull("managed username must not be null", username);
        AssertUtils.domainPropNotNull("managed user email must not be null", email);
        AssertUtils.domainPropNotNull("managed user status must not be null", status);
        AssertUtils.domainPropNotNull("deleted must not be null", deleted);
    }
}
