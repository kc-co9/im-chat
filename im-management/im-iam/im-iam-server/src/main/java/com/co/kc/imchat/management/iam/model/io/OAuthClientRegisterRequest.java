package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.model.enums.IamOAuthGrantTypeEnum;

import java.util.Set;

/** 注册 IAM 浏览器或机器 OAuth 客户端请求。 */
public record OAuthClientRegisterRequest(
        String appKey,
        Long audienceAppId,
        String name,
        String clientId,
        String clientSecret,
        Set<IamOAuthGrantTypeEnum> grantTypes,
        Set<String> scopes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris
) {
    public OAuthClientRegisterRequest {
        AssertUtils.argNotBlank("appKey must not be blank", appKey);
        AssertUtils.argNotNull("audienceAppId must not be null", audienceAppId);
        AssertUtils.argNotBlank("oauth client name must not be blank", name);
        AssertUtils.argNotBlank("clientId must not be blank", clientId);
        AssertUtils.argNotBlank("client secret must not be blank", clientSecret);
        AssertUtils.argNotEmpty("oauth client grant types must not be empty", grantTypes);
        AssertUtils.argNotEmpty("oauth client scopes must not be empty", scopes);
        AssertUtils.allArgNotNull(
                "oauth client redirect URI sets must not be null",
                redirectUris,
                postLogoutRedirectUris);
        grantTypes = Set.copyOf(grantTypes);
        scopes = Set.copyOf(scopes);
        redirectUris = Set.copyOf(redirectUris);
        postLogoutRedirectUris = Set.copyOf(postLogoutRedirectUris);
    }
}
