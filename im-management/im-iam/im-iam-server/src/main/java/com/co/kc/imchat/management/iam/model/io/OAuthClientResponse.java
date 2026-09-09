package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/** 新注册 IAM OAuth 客户端的 HTTP 响应。 */
public record OAuthClientResponse(
        String clientId,
        Long appId,
        Long audienceAppId,
        String name,
        Set<String> scopes,
        String status
) {
    public OAuthClientResponse {
        scopes = Set.copyOf(scopes);
    }
}
