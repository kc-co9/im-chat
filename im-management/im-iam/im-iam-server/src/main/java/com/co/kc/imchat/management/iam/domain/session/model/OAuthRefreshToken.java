package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth Refresh Token 的持久化状态。 */
public record OAuthRefreshToken(
        /* Refresh Token 的不可逆摘要。 */
        OAuthCredentialDigest digest,
        /* Refresh Token 的有效区间。 */
        OAuthCredentialPeriod period
) {
    public OAuthRefreshToken {
        AssertUtils.allDomainPropNotNull(
                "OAuth refresh token required properties must not be null",
                digest, period);
    }
}
