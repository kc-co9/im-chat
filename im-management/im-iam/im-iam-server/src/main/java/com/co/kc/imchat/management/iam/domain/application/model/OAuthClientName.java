package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth 客户端名称。 */
public record OAuthClientName(String value) {
    public OAuthClientName {
        AssertUtils.domainPropNotBlank("oauth client name must not be blank", value);
    }
}
