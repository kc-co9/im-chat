package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth2 客户端安全存储密钥。 */
public record OAuthClientSecret(String value) {
    public OAuthClientSecret {
        AssertUtils.domainPropNotBlank("stored client secret must not be blank", value);
    }
}
