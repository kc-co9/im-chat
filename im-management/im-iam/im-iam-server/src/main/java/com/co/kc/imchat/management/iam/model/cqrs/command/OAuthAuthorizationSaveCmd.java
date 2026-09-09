package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationRequestDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthCredentialDTO;

import java.time.Instant;
import java.util.Set;

/** 保存 Spring OAuth 授权状态的应用命令。 */
public record OAuthAuthorizationSaveCmd(
        /* OAuth 授权业务标识。 */
        String authorizationId,
        /* OAuth 客户端标识。 */
        String oauthClientId,
        /* 授权主体类型。 */
        OAuthPrincipalType principalType,
        /* 授权主体标识。 */
        String principal,
        /* OAuth 授权模式。 */
        OAuthGrantType grantType,
        /* 浏览器授权请求状态。 */
        OAuthAuthorizationRequestDTO request,
        /* 授权码状态。 */
        OAuthCredentialDTO authorizationCode,
        /* Access Token 状态。 */
        OAuthCredentialDTO accessToken,
        /* Refresh Token 状态。 */
        OAuthCredentialDTO refreshToken,
        /* OIDC ID Token 状态。 */
        OAuthCredentialDTO idToken,
        /* 已授权 Scope。 */
        Set<String> scopes,
        /* 授权状态。 */
        OAuthAuthorizationStatus status,
        /* 授权撤销时间。 */
        Instant revokedAt
) {
    public OAuthAuthorizationSaveCmd {
        AssertUtils.argNotNull("OAuth grant type must not be null", grantType);
        if (principalType == null) {
            principalType = grantType == OAuthGrantType.CLIENT_CREDENTIALS
                    ? OAuthPrincipalType.CLIENT
                    : OAuthPrincipalType.ADMINISTRATOR;
        }
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
