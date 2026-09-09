package com.co.kc.imchat.management.iam.transformer.infrastructure;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationSaveCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationRequestDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthCredentialDTO;
import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.attributes.OAuthAuthorizationAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Spring Authorization Server 授权对象与持久化状态之间的转换器。
 */
@RequiredArgsConstructor
public class OAuthAuthorizationTransformer {
    private static final String AUTHORIZATION_CODE = OAuth2ParameterNames.CODE;
    private static final String ID_TOKEN = "id_token";
    private final Sha256OAuthTokenDigester tokenDigester;
    private final Clock clock;

    /**
     * 将 Spring 授权状态转换为保存授权命令。
     */
    public OAuthAuthorizationSaveCmd saveCommandFrom(OAuth2Authorization authorization) {
        OAuth2AuthorizationRequest request = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        OAuthPrincipalType principalType = authorization.getAttribute(OAuthAuthorizationAttributes.PRINCIPAL_TYPE);
        return new OAuthAuthorizationSaveCmd(
                authorization.getId(),
                authorization.getRegisteredClientId(),
                principalType,
                authorization.getPrincipalName(),
                oauthGrantTypeFrom(authorization.getAuthorizationGrantType()),
                requestFrom(request, authorization.getAuthorizedScopes()),
                authorizationCodeStateFrom(authorization),
                accessTokenFrom(authorization),
                refreshTokenFrom(authorization),
                idTokenFrom(authorization),
                Set.copyOf(authorization.getAuthorizedScopes()),
                OAuthAuthorizationStatus.ACTIVE,
                null);
    }

    private boolean authorizationCodeInvalidated(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AuthorizationCode> holder =
                authorization.getToken(OAuth2AuthorizationCode.class);
        return holder != null && holder.isInvalidated();
    }

    /**
     * 将 Spring OAuth 凭据类型转换为领域凭据类型。
     */
    public OAuthCredentialType credentialTypeFrom(OAuth2TokenType tokenType) {
        return tokenType == null ? null : credentialTypeFrom(tokenType.getValue());
    }

    private OAuthCredentialType credentialTypeFrom(String tokenType) {
        if (tokenType == null) {
            return null;
        }
        if (AUTHORIZATION_CODE.equals(tokenType)) {
            return OAuthCredentialType.AUTHORIZATION_CODE;
        }
        if (OAuth2TokenType.ACCESS_TOKEN.getValue().equals(tokenType)) {
            return OAuthCredentialType.ACCESS_TOKEN;
        }
        if (OAuth2TokenType.REFRESH_TOKEN.getValue().equals(tokenType)) {
            return OAuthCredentialType.REFRESH_TOKEN;
        }
        if (ID_TOKEN.equals(tokenType)) {
            return OAuthCredentialType.ID_TOKEN;
        }
        return null;
    }

    /**
     * 从持久化状态重建 Spring 授权对象。
     *
     * @param stored        持久化状态
     * @param client        已解析的 Spring OAuth 客户端
     * @param rawToken      本次请求提交的原始 Token
     * @param requestedType 本次请求的 Token 类型
     * @return Spring 授权对象
     */
    public OAuth2Authorization authorizationFrom(
            OAuthAuthorizationDTO stored,
            RegisteredClient client,
            String rawToken,
            OAuth2TokenType requestedType
    ) {
        UsernamePasswordAuthenticationToken principal =
                UsernamePasswordAuthenticationToken.authenticated(stored.principal(), "N/A", Collections.emptyList());
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(stored.authorizationId())
                .principalName(stored.principal())
                .authorizationGrantType(new AuthorizationGrantType(grantType(stored.grantType())))
                .authorizedScopes(stored.scopes())
                .attribute(Principal.class.getName(), principal);
        builder.attribute(OAuthAuthorizationAttributes.PRINCIPAL_TYPE, stored.principalType());
        builder.attribute(OAuthAuthorizationAttributes.PRINCIPAL, stored.principal());
        if (stored.grantType() == OAuthGrantType.AUTHORIZATION_CODE) {
            OAuthAuthorizationRequestDTO storedRequest = stored.request();
            OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(storedRequest.authorizationUri())
                    .clientId(client.getClientId())
                    .redirectUri(storedRequest.redirectUri())
                    .scopes(storedRequest.scopes())
                    .state(storedRequest.state())
                    .additionalParameters(parameters(stored))
                    .build();
            builder.attribute(OAuth2AuthorizationRequest.class.getName(), request);
        }
        addAuthorizationCode(builder, stored, rawToken, requestedType);
        addAccessToken(builder, stored, rawToken, requestedType);
        addRefreshToken(builder, stored, rawToken, requestedType);
        addIdToken(builder, stored, rawToken, requestedType);
        return builder.build();
    }

    private OAuthCredentialDTO authorizationCodeStateFrom(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AuthorizationCode> holder =
                authorization.getToken(OAuth2AuthorizationCode.class);
        if (holder == null) {
            return null;
        }
        OAuth2AuthorizationCode token = holder.getToken();
        Instant usedAt = authorizationCodeInvalidated(authorization)
                ? clock.instant()
                : null;
        return new OAuthCredentialDTO(
                digest(token.getTokenValue()),
                token.getIssuedAt(),
                token.getExpiresAt(),
                null,
                usedAt);
    }

    private OAuthCredentialDTO accessTokenFrom(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> holder = authorization.getAccessToken();
        if (holder == null) {
            return null;
        }
        OAuth2AccessToken token = holder.getToken();
        return new OAuthCredentialDTO(
                digest(token.getTokenValue()),
                token.getIssuedAt(),
                token.getExpiresAt(),
                holder.getClaims(),
                null);
    }

    private OAuthCredentialDTO refreshTokenFrom(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> holder = authorization.getRefreshToken();
        if (holder == null) {
            return null;
        }
        OAuth2RefreshToken token = holder.getToken();
        return new OAuthCredentialDTO(
                digest(token.getTokenValue()),
                token.getIssuedAt(),
                token.getExpiresAt(),
                null,
                null);
    }

    private OAuthCredentialDTO idTokenFrom(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OidcIdToken> holder = authorization.getToken(OidcIdToken.class);
        if (holder == null) {
            return null;
        }
        OidcIdToken token = holder.getToken();
        return new OAuthCredentialDTO(
                digest(token.getTokenValue()),
                token.getIssuedAt(),
                token.getExpiresAt(),
                token.getClaims(),
                null);
    }

    private void addAuthorizationCode(
            OAuth2Authorization.Builder builder,
            OAuthAuthorizationDTO stored,
            String rawToken,
            OAuth2TokenType requestedType
    ) {
        OAuthCredentialDTO storedCode = stored.authorizationCode();
        if (storedCode == null) {
            return;
        }
        String value = tokenValue(
                storedCode.digest(), rawToken, requestedType, AUTHORIZATION_CODE);
        OAuth2AuthorizationCode code = new OAuth2AuthorizationCode(
                value,
                storedCode.issuedAt(),
                storedCode.expiresAt());
        builder.token(code, metadata -> {
            if (storedCode.usedAt() != null || stored.revoked()) {
                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
            }
        });
    }

    private void addAccessToken(
            OAuth2Authorization.Builder builder,
            OAuthAuthorizationDTO stored,
            String rawToken,
            OAuth2TokenType requestedType
    ) {
        OAuthCredentialDTO storedToken = stored.accessToken();
        if (storedToken == null) {
            return;
        }
        String value = tokenValue(
                storedToken.digest(), rawToken, requestedType,
                OAuth2TokenType.ACCESS_TOKEN.getValue());
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                value,
                storedToken.issuedAt(),
                storedToken.expiresAt(),
                stored.scopes());
        builder.token(token, metadata -> {
            metadata.put(
                    OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                    springClaimsFrom(storedToken));
            if (stored.revoked()) {
                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
            }
        });
    }

    private void addRefreshToken(
            OAuth2Authorization.Builder builder,
            OAuthAuthorizationDTO stored,
            String rawToken,
            OAuth2TokenType requestedType
    ) {
        OAuthCredentialDTO storedToken = stored.refreshToken();
        if (storedToken == null) {
            return;
        }
        String value = tokenValue(
                storedToken.digest(), rawToken, requestedType,
                OAuth2TokenType.REFRESH_TOKEN.getValue());
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                value,
                storedToken.issuedAt(),
                storedToken.expiresAt());
        builder.token(refreshToken, metadata -> {
            if (stored.revoked()) {
                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
            }
        });
    }

    private void addIdToken(
            OAuth2Authorization.Builder builder,
            OAuthAuthorizationDTO stored,
            String rawToken,
            OAuth2TokenType requestedType
    ) {
        OAuthCredentialDTO storedToken = stored.idToken();
        if (storedToken == null) {
            return;
        }
        String value = tokenValue(
                storedToken.digest(), rawToken, requestedType, ID_TOKEN);
        OidcIdToken idToken = new OidcIdToken(
                value,
                storedToken.issuedAt(),
                storedToken.expiresAt(),
                springClaimsFrom(storedToken));
        builder.token(idToken, metadata -> {
            if (stored.revoked()) {
                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
            }
        });
    }

    private String tokenValue(
            String digest,
            String rawToken,
            OAuth2TokenType requestedType,
            String expectedType
    ) {
        if (rawToken == null) {
            return digest;
        }
        if (requestedType != null) {
            return expectedType.equals(requestedType.getValue()) ? rawToken : digest;
        }
        return tokenDigester.matches(rawToken, digest) ? rawToken : digest;
    }

    private Map<String, Object> springClaimsFrom(OAuthCredentialDTO credential) {
        Map<String, Object> claims = new LinkedHashMap<>(credential.claims());
        claims.put("iat", credential.issuedAt());
        claims.put("exp", credential.expiresAt());
        Object notBefore = claims.get("nbf");
        if (notBefore != null && !(notBefore instanceof Instant)) {
            claims.put("nbf", instantFrom(notBefore));
        }
        return Map.copyOf(claims);
    }

    private Instant instantFrom(Object value) {
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        try {
            return Instant.parse(value.toString());
        } catch (DateTimeParseException exception) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(value.toString()));
            } catch (NumberFormatException numberFormatException) {
                throw new IllegalStateException("OAuth token time claim is invalid", exception);
            }
        }
    }

    private String stringParameter(OAuth2AuthorizationRequest request, String name) {
        Object value = request.getAdditionalParameters().get(name);
        if (value == null) {
            throw new IllegalStateException("Required OAuth2 parameter is missing: " + name);
        }
        return value.toString();
    }

    private Map<String, Object> parameters(OAuthAuthorizationDTO stored) {
        OAuthAuthorizationRequestDTO request = stored.request();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("code_challenge", request.codeChallenge());
        parameters.put("code_challenge_method", request.codeChallengeMethod());
        return parameters;
    }

    private OAuthAuthorizationRequestDTO requestFrom(
            OAuth2AuthorizationRequest request,
            Set<String> authorizedScopes
    ) {
        if (request == null) {
            return null;
        }
        return new OAuthAuthorizationRequestDTO(
                request.getAuthorizationUri(),
                request.getRedirectUri(),
                request.getState(),
                stringParameter(request, "code_challenge"),
                stringParameter(request, "code_challenge_method"),
                Set.copyOf(request.getScopes().isEmpty()
                        ? authorizedScopes : request.getScopes()));
    }

    private String digest(String value) {
        return tokenDigester.digest(value);
    }

    public OAuthGrantType oauthGrantTypeFrom(AuthorizationGrantType grantType) {
        return OAuthGrantType.valueOf(grantType.getValue().toUpperCase(Locale.ROOT));
    }

    private String grantType(OAuthGrantType grantType) {
        return grantType.name().toLowerCase(Locale.ROOT);
    }

}
