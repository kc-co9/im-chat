package com.co.kc.imchat.management.iam.sdk.oauth;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * OAuth2 登录发起时保存的一次性 PKCE 与页面恢复上下文。
 */
public record IamAuthorizationRequest(
        String codeVerifier,
        String continuePath
) {
    public IamAuthorizationRequest {
        AssertUtils.argNotBlank("code verifier must not be blank", codeVerifier);
        AssertUtils.argNotBlank("continue path must not be blank", continuePath);
    }
}
