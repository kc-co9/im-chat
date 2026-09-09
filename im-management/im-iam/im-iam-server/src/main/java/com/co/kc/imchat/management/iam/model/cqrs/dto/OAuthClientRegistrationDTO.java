package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;

import java.util.Set;

/** Spring Authorization Server 所需的 OAuth Client 注册信息。 */
public record OAuthClientRegistrationDTO(
        /* OAuth Client 标识。 */
        String clientId,
        /* 已编码的 OAuth Client Secret。 */
        String encodedSecret,
        /* OAuth Client 名称。 */
        String name,
        /* 允许的授权类型。 */
        Set<OAuthGrantType> grantTypes,
        /* 允许的 Scope。 */
        Set<String> scopes,
        /* 登录回调地址。 */
        Set<String> redirectUris,
        /* 登出回调地址。 */
        Set<String> postLogoutRedirectUris
) {
    public OAuthClientRegistrationDTO {
        AssertUtils.argNotBlank("oauth client id must not be blank", clientId);
        AssertUtils.argNotBlank("encoded oauth client secret must not be blank", encodedSecret);
        AssertUtils.argNotBlank("oauth client name must not be blank", name);
        AssertUtils.allArgNotNull(
                "oauth client registration collections must not be null",
                grantTypes, scopes, redirectUris, postLogoutRedirectUris);
        grantTypes = Set.copyOf(grantTypes);
        scopes = Set.copyOf(scopes);
        redirectUris = Set.copyOf(redirectUris);
        postLogoutRedirectUris = Set.copyOf(postLogoutRedirectUris);
    }

    @Override
    public String toString() {
        return "OAuthClientRegistrationDTO[clientId=" + clientId
                + ", encodedSecret=***, name=" + name
                + ", grantTypes=" + grantTypes
                + ", scopes=" + scopes
                + ", redirectUris=" + redirectUris
                + ", postLogoutRedirectUris=" + postLogoutRedirectUris + "]";
    }
}
