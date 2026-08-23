package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/** 已认证的 Access credential。 */
public record AccessCredential(UserId userId, SessionVersion sessionVersion, Instant expiresAt) {

    public AccessCredential {
        validate(userId, sessionVersion, expiresAt);
    }

    private static void validate(UserId userId, SessionVersion sessionVersion, Instant expiresAt) {
        AssertUtils.domainPropNotNull("userId must not be null", userId);
        AssertUtils.domainPropNotNull("version must not be null", sessionVersion);
        AssertUtils.domainPropNotNull("expiresAt must not be null", expiresAt);
    }
}
