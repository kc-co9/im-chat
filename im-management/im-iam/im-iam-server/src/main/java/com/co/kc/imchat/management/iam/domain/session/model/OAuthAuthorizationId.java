package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth 授权聚合标识。 */
public record OAuthAuthorizationId(String value) {
    public OAuthAuthorizationId {
        AssertUtils.domainPropNotBlank("OAuth grant id must not be blank", value);
    }
}
