package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/** 已认证的 Refresh credential 及其不可逆 fingerprint。 */
public record RefreshCredential(
        UserId userId,
        SessionVersion sessionVersion,
        Instant expiresAt,
        RefreshFingerprint fingerprint
) {
    public RefreshCredential {
        AssertUtils.domainPropNotNull("userId must not be null", userId);
        AssertUtils.domainPropNotNull("version must not be null", sessionVersion);
        AssertUtils.domainPropNotNull("expiresAt must not be null", expiresAt);
        AssertUtils.domainPropNotNull("fingerprint must not be null", fingerprint);
    }
}
