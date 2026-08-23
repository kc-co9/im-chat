package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/** 已签发的 Access Token 及其可信过期时间。 */
public record IssuedAccessToken(AccessToken token, Instant expiresAt) {

    public IssuedAccessToken {
        AssertUtils.domainPropNotNull("token must not be null", token);
        AssertUtils.domainPropNotNull("expiresAt must not be null", expiresAt);
    }
}
