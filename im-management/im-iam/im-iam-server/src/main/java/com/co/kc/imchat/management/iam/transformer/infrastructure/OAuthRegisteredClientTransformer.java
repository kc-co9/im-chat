package com.co.kc.imchat.management.iam.transformer.infrastructure;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientRegistrationDTO;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;

/**
 * OAuth Client 领域值到 Spring Authorization Server 类型的转换器。
 */
public final class OAuthRegisteredClientTransformer {
    private static final Duration AUTHORIZATION_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofHours(8);
    public static final OAuthRegisteredClientTransformer INSTANCE =
            new OAuthRegisteredClientTransformer();

    private OAuthRegisteredClientTransformer() {
    }

    /**
     * 将应用层 OAuth Client 注册信息转换为 Spring 客户端。
     */
    public RegisteredClient registeredClientFrom(OAuthClientRegistrationDTO client) {
        boolean browserClient = client.grantTypes()
                .contains(OAuthGrantType.AUTHORIZATION_CODE);
        RegisteredClient.Builder builder = RegisteredClient.withId(client.clientId())
                .clientId(client.clientId())
                .clientSecret(client.encodedSecret())
                .clientName(client.name())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(browserClient)
                        .requireAuthorizationConsent(false)
                        .build());
        client.grantTypes().stream().map(this::authorizationGrantTypeFrom).forEach(builder::authorizationGrantType);
        client.scopes().forEach(builder::scope);
        client.redirectUris().forEach(builder::redirectUri);
        client.postLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);

        TokenSettings.Builder tokenSettings = TokenSettings.builder()
                .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                .accessTokenFormat(OAuth2TokenFormat.REFERENCE);
        if (browserClient) {
            tokenSettings.authorizationCodeTimeToLive(AUTHORIZATION_CODE_TTL)
                    .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                    .reuseRefreshTokens(false);
        }
        return builder.tokenSettings(tokenSettings.build()).build();
    }

    /**
     * 将 OAuth Grant Type 转换为 Spring 类型。
     */
    public AuthorizationGrantType authorizationGrantTypeFrom(
            OAuthGrantType grantType
    ) {
        return switch (grantType) {
            case AUTHORIZATION_CODE -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case REFRESH_TOKEN -> AuthorizationGrantType.REFRESH_TOKEN;
            case CLIENT_CREDENTIALS -> AuthorizationGrantType.CLIENT_CREDENTIALS;
        };
    }
}
