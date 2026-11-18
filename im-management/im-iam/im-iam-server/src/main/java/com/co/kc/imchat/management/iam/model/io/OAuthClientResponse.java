package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.management.iam.model.enums.IamOAuthClientTypeEnum;

import java.util.Set;

/** 新注册 IAM OAuth 客户端的 HTTP 响应。 */
public record OAuthClientResponse(
        String clientId,
        IamOAuthClientTypeEnum clientType,
        Long appId,
        Long audienceAppId,
        String name,
        Set<String> scopes,
        String status
) {
    public OAuthClientResponse {
        clientType = clientType == null ? IamOAuthClientTypeEnum.CONFIDENTIAL : clientType;
        scopes = Set.copyOf(scopes);
    }
}
