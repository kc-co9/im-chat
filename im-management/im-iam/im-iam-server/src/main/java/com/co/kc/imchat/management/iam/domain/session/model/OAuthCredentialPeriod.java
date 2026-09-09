package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/** OAuth 凭据的签发与过期区间。 */
public record OAuthCredentialPeriod(
        /* 凭据签发时间。 */
        Instant issuedAt,
        /* 凭据过期时间。 */
        Instant expiresAt
) {
    public OAuthCredentialPeriod {
        AssertUtils.allDomainPropNotNull(
                "OAuth credential period must not be null",
                issuedAt, expiresAt);
        AssertUtils.domainPropTrue(
                "OAuth credential expiration must be after issuance",
                expiresAt.isAfter(issuedAt));
    }

    public boolean contains(Instant instant) {
        AssertUtils.domainPropNotNull("OAuth credential time must not be null", instant);
        return !instant.isBefore(issuedAt) && instant.isBefore(expiresAt);
    }
}
