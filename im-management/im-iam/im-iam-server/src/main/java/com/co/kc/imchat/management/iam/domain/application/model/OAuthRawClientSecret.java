package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 仅在应用注册边界短暂存在的 OAuth2 客户端明文密钥。 */
public record OAuthRawClientSecret(String value) {
    private static final int MINIMUM_LENGTH = 20;

    public OAuthRawClientSecret {
        AssertUtils.domainPropNotBlank("client secret must not be blank", value);
        AssertUtils.domainPropTrue(
                "client secret must contain at least 20 characters",
                value.length() >= MINIMUM_LENGTH);
    }
}
