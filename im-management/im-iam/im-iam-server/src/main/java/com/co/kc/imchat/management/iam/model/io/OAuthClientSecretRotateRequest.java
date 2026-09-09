package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 轮换 IAM OAuth 客户端密钥请求。 */
public record OAuthClientSecretRotateRequest(String clientId, String clientSecret) {
    public OAuthClientSecretRotateRequest {
        AssertUtils.argNotBlank("client secret must not be blank", clientSecret);
    }
}
