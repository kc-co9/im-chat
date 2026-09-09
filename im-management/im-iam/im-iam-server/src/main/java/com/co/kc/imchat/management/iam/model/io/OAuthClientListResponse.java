package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.model.enums.IamOAuthGrantTypeEnum;

import java.util.Set;

/** IAM OAuth 客户端列表 HTTP 响应，不包含客户端密钥或授权数据。 */
public record OAuthClientListResponse(
        String clientId,
        Long appId,
        Long audienceAppId,
        String audienceAppKey,
        String name,
        Set<IamOAuthGrantTypeEnum> grantTypes,
        Set<String> scopes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris,
        String status
) {
    public OAuthClientListResponse {
        AssertUtils.allArgNotNull(
                "oauth client list response must not contain null values",
                clientId,
                appId,
                audienceAppId,
                audienceAppKey,
                name,
                grantTypes,
                scopes,
                redirectUris,
                postLogoutRedirectUris,
                status);
        grantTypes = Set.copyOf(grantTypes);
        scopes = Set.copyOf(scopes);
        redirectUris = Set.copyOf(redirectUris);
        postLogoutRedirectUris = Set.copyOf(postLogoutRedirectUris);
    }
}
