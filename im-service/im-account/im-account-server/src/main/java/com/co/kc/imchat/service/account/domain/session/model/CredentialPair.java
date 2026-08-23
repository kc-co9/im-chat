package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 同一 Session version 对应的 Access/Refresh credential 对。 */
public record CredentialPair(
        IssuedAccessToken access,
        IssuedRefreshToken refresh
) {
    public CredentialPair {
        AssertUtils.domainPropNotNull("access must not be null", access);
        AssertUtils.domainPropNotNull("refresh must not be null", refresh);
    }
}
