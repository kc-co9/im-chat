package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Set;

/** 更新 OAuth 客户端访问配置。 */
public record OAuthClientAccessUpdateCmd(
        String clientId,
        Set<String> scopes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris
) {
    public OAuthClientAccessUpdateCmd {
        AssertUtils.argNotBlank("clientId must not be blank", clientId);
        AssertUtils.allArgNotNull(
                "oauth client access update must not contain null collections",
                scopes,
                redirectUris,
                postLogoutRedirectUris);
        scopes = Set.copyOf(scopes);
        redirectUris = Set.copyOf(redirectUris);
        postLogoutRedirectUris = Set.copyOf(postLogoutRedirectUris);
    }
}
