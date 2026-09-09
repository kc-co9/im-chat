package com.co.kc.imchat.management.iam.infrastructure.security.token;

import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAdministratorTokenClaimsDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthApplicationTokenClaimsDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthAdministratorTokenClaimsQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthApplicationTokenClaimsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;

import java.util.List;

/** 为 Token 写入协议主体、应用归属与签发时权限快照。 */
@RequiredArgsConstructor
public class OAuthTokenClaimsCustomizer {
    private final OAuthClientAppService oauthClientAppService;

    public void customizeAccessToken(OAuth2TokenClaimsContext context) {
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(
                context.getAuthorizationGrantType())) {
            OAuthApplicationTokenClaimsDTO claims =
                    oauthClientAppService.getApplicationTokenClaims(
                            new OAuthApplicationTokenClaimsQuery(
                                    context.getRegisteredClient().getId()));
            context.getClaims()
                    .subject(claims.subject())
                    .claim("appKey", claims.sourceAppKey())
                    .claim("aud", List.of(claims.audienceAppKey()))
                    .claim("clientId", context.getRegisteredClient().getClientId());
            return;
        }

        OAuthAdministratorTokenClaimsDTO claims =
                oauthClientAppService.getAdministratorTokenClaims(
                        new OAuthAdministratorTokenClaimsQuery(
                                context.getRegisteredClient().getId(),
                                Long.valueOf(context.getPrincipal().getName())));
        context.getClaims()
                .subject(claims.subject())
                .claim("username", claims.username())
                .claim("appKey", claims.sourceAppKey())
                .claim("aud", List.of(claims.audienceAppKey()))
                .claim("clientId", context.getRegisteredClient().getClientId())
                .claim("authorities", claims.authorities());
    }

    public void customizeIdToken(JwtEncodingContext context) {
        OAuthAdministratorTokenClaimsDTO claims =
                oauthClientAppService.getAdministratorTokenClaims(
                        new OAuthAdministratorTokenClaimsQuery(
                                context.getRegisteredClient().getId(),
                                Long.valueOf(context.getPrincipal().getName())));
        context.getClaims()
                .subject(claims.subject())
                .claim("username", claims.username());
    }
}
