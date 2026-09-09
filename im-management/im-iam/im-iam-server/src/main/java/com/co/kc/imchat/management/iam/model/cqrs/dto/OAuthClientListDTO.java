package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientStatus;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;

import java.util.Set;

/** IAM OAuth 客户端列表项，不包含客户端密钥或授权数据。 */
public record OAuthClientListDTO(
        String clientId,
        Long appId,
        Long audienceAppId,
        String audienceAppKey,
        String name,
        Set<OAuthGrantType> grantTypes,
        Set<String> scopes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris,
        OAuthClientStatus status
) {
    public OAuthClientListDTO {
        AssertUtils.allArgNotNull(
                "oauth client list DTO must not contain null values",
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
