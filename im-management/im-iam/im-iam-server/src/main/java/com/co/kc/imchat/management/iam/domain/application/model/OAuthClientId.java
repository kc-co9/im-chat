package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth2 客户端标识。 */
public record OAuthClientId(String value) {
    public OAuthClientId {
        AssertUtils.domainPropNotBlank("clientId must not be blank", value);
    }
}
