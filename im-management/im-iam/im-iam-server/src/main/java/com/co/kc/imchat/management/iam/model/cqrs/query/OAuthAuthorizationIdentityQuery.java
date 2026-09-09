package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;

/** OAuth 授权主体上下文查询。 */
public record OAuthAuthorizationIdentityQuery(
        String oauthClientId,
        String principalName,
        OAuthGrantType grantType
) {
    public OAuthAuthorizationIdentityQuery {
        AssertUtils.argNotBlank("OAuth client id must not be blank", oauthClientId);
        AssertUtils.argNotNull("OAuth grant type must not be null", grantType);
        if (grantType != OAuthGrantType.CLIENT_CREDENTIALS) {
            AssertUtils.argNotBlank("principal name must not be blank", principalName);
        }
    }
}
