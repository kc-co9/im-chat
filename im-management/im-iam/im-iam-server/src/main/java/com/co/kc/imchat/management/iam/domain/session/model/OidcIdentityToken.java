package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Map;

/** OIDC ID Token 的持久化状态。 */
public record OidcIdentityToken(
        /* ID Token 的不可逆摘要。 */
        OAuthCredentialDigest digest,
        /* ID Token 的有效区间。 */
        OAuthCredentialPeriod period,
        /* ID Token 的非敏感 Claims。 */
        Map<String, Object> claims
) {
    public OidcIdentityToken {
        AssertUtils.allDomainPropNotNull(
                "OIDC ID token required properties must not be null",
                digest, period);
        claims = claims == null ? Map.of() : Map.copyOf(claims);
    }
}
