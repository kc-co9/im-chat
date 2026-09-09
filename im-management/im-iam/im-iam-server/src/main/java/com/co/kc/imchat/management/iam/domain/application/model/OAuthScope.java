package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 机器客户端获准申请的 OAuth2 Scope。 */
public record OAuthScope(String value) {
    public OAuthScope {
        AssertUtils.domainPropNotBlank("client scope must not be blank", value);
    }
}
