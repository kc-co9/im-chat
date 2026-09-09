package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/** 更新 OAuth 客户端访问配置的 HTTP 请求。 */
public record OAuthClientAccessUpdateRequest(
        String clientId,
        Set<String> scopes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris
) {
}
