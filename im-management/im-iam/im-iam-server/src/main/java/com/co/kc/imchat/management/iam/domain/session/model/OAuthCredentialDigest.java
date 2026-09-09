package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth 凭据的不可逆摘要。 */
public record OAuthCredentialDigest(
        /* 凭据的不可逆摘要值。 */
        String value
) {
    public OAuthCredentialDigest {
        AssertUtils.domainPropNotBlank("OAuth credential digest must not be blank", value);
    }
}
