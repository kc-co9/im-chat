package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Map;

/** OAuth Access Token 的持久化状态。 */
public record OAuthAccessToken(
        /* Token 的不可逆摘要。 */
        OAuthCredentialDigest digest,
        /* Token 的签发与过期区间。 */
        OAuthCredentialPeriod period,
        /* Token 的非敏感 Claims。 */
        Map<String, Object> claims
) {
    public OAuthAccessToken {
        AssertUtils.allDomainPropNotNull(
                "OAuth access token required properties must not be null",
                digest, period);
        claims = claims == null ? Map.of() : Map.copyOf(claims);
    }
}
