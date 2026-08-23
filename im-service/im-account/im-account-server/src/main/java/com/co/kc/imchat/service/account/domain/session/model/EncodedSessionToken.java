package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/** 已编码的会话令牌及其有效期。 */
public record EncodedSessionToken(String value, Instant expiresAt) {

    public EncodedSessionToken {
        AssertUtils.domainPropNotBlank("token must not be blank", value);
        AssertUtils.domainPropNotNull("expiresAt must not be null", expiresAt);
    }
}
