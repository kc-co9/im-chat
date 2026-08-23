package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/** 已签发的 Refresh Token、可信过期时间及不可逆指纹。 */
public record IssuedRefreshToken(RefreshToken token, Instant expiresAt, RefreshFingerprint fingerprint) {

    public IssuedRefreshToken {
        AssertUtils.domainPropNotNull("token must not be null", token);
        AssertUtils.domainPropNotNull("expiresAt must not be null", expiresAt);
        AssertUtils.domainPropNotNull("fingerprint must not be null", fingerprint);
    }
}
