package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Set;

/** IAM OAuth 客户端信息，不包含客户端密钥。 */
public record OAuthClientDTO(
        String clientId,
        Long appId,
        Long audienceAppId,
        String name,
        Set<String> scopes,
        String status
) {
    public OAuthClientDTO {
        AssertUtils.allArgNotNull(
                "oauth client DTO must not contain null values",
                clientId,
                appId,
                audienceAppId,
                name,
                scopes,
                status);
        scopes = Set.copyOf(scopes);
    }
}
