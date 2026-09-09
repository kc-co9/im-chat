package com.co.kc.imchat.management.iam.transformer.db;

import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAccessToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationCode;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialPeriod;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OidcIdentityToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipal;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthRefreshToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationRequest;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamOAuthAuthorizationStatus;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** OAuth 授权聚合与扁平数据库实体转换器。 */
public final class OAuthAuthorizationDbTransformer {
    public static final OAuthAuthorizationDbTransformer INSTANCE = new OAuthAuthorizationDbTransformer();

    private OAuthAuthorizationDbTransformer() {
    }

    /** 从数据库状态重建并校验 OAuth 授权聚合。 */
    public OAuthAuthorization oauthAuthorizationFrom(DbIamOAuthAuthorization entity) {
        OAuthAuthorization authorization = OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId(entity.getAuthorizationId()))
                .appId(new AppId(entity.getAppId()))
                .clientId(new OAuthClientId(entity.getOauthClientId()))
                .principal(new OAuthPrincipal(
                        OAuthPrincipalType.valueOf(entity.getPrincipalType()),
                        entity.getPrincipalName()))
                .grantType(grantType(entity.getAuthorizationGrantType()))
                .request(request(entity))
                .authorizationCode(authorizationCode(entity))
                .accessToken(accessToken(entity))
                .refreshToken(refreshToken(entity))
                .idToken(idToken(entity))
                .scopes(scopes(entity.getScope()))
                .status(OAuthAuthorizationStatus.valueOf(entity.getStatus().name()))
                .revokedAt(entity.getRevokedAt())
                .build();
        if (entity.getId() != null) {
            authorization.setPkId(entity.getId());
        }
        return authorization;
    }

    /** 将 OAuth 授权聚合展开为数据库实体。 */
    public DbIamOAuthAuthorization dbAuthorizationFrom(OAuthAuthorization authorization) {
        DbIamOAuthAuthorization entity = new DbIamOAuthAuthorization();
        entity.setId(authorization.getPkId());
        entity.setAuthorizationId(authorization.getId().value());
        entity.setAppId(authorization.getAppId().value());
        entity.setOauthClientId(authorization.getClientId().value());
        entity.setPrincipalType(authorization.getPrincipal().type().name());
        entity.setPrincipalName(authorization.getPrincipal().value());
        entity.setAuthorizationGrantType(
                authorization.getGrantType().name().toLowerCase(Locale.ROOT));
        mapRequest(authorization.getRequest(), entity);
        mapAuthorizationCode(authorization.getAuthorizationCode(), entity);
        mapAccessToken(authorization.getAccessToken(), entity);
        mapRefreshToken(authorization.getRefreshToken(), entity);
        mapIdToken(authorization.getIdToken(), entity);
        entity.setScope(join(authorization.getScopes()));
        entity.setStatus(DbIamOAuthAuthorizationStatus.valueOf(authorization.getStatus().name()));
        entity.setRevokedAt(authorization.getRevokedAt());
        return entity;
    }

    private OAuthGrantType grantType(String value) {
        return OAuthGrantType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private OAuthAuthorizationRequest request(DbIamOAuthAuthorization entity) {
        if (grantType(entity.getAuthorizationGrantType()) != OAuthGrantType.AUTHORIZATION_CODE) {
            return null;
        }
        return new OAuthAuthorizationRequest(
                entity.getAuthorizationUri(),
                entity.getRedirectUri(),
                entity.getState(),
                entity.getCodeChallenge(),
                entity.getCodeChallengeMethod(),
                scopes(entity.getRequestedScope()));
    }

    private OAuthAuthorizationCode authorizationCode(DbIamOAuthAuthorization entity) {
        if (entity.getAuthorizationCodeDigest() == null) {
            return null;
        }
        return new OAuthAuthorizationCode(
                new OAuthCredentialDigest(entity.getAuthorizationCodeDigest()),
                new OAuthCredentialPeriod(
                        entity.getAuthorizationCodeIssuedAt(),
                        entity.getAuthorizationCodeExpiresAt()),
                entity.getAuthorizationCodeUsedAt());
    }

    private OAuthAccessToken accessToken(DbIamOAuthAuthorization entity) {
        if (entity.getAccessTokenDigest() == null) {
            return null;
        }
        return new OAuthAccessToken(
                new OAuthCredentialDigest(entity.getAccessTokenDigest()),
                new OAuthCredentialPeriod(
                        entity.getAccessTokenIssuedAt(),
                        entity.getAccessTokenExpiresAt()),
                entity.getAccessTokenClaims());
    }

    private OAuthRefreshToken refreshToken(DbIamOAuthAuthorization entity) {
        if (entity.getRefreshTokenDigest() == null) {
            return null;
        }
        return new OAuthRefreshToken(
                new OAuthCredentialDigest(entity.getRefreshTokenDigest()),
                new OAuthCredentialPeriod(
                        entity.getRefreshTokenIssuedAt(),
                        entity.getRefreshTokenExpiresAt()));
    }

    private OidcIdentityToken idToken(DbIamOAuthAuthorization entity) {
        if (entity.getIdTokenDigest() == null) {
            return null;
        }
        return new OidcIdentityToken(
                new OAuthCredentialDigest(entity.getIdTokenDigest()),
                new OAuthCredentialPeriod(
                        entity.getIdTokenIssuedAt(),
                        entity.getIdTokenExpiresAt()),
                entity.getIdTokenClaims());
    }

    private void mapRequest(OAuthAuthorizationRequest request, DbIamOAuthAuthorization entity) {
        entity.setAuthorizationUri(request == null ? null : request.authorizationUri());
        entity.setRedirectUri(request == null ? null : request.redirectUri());
        entity.setState(request == null ? null : request.state());
        entity.setCodeChallenge(request == null ? null : request.codeChallenge());
        entity.setCodeChallengeMethod(request == null ? null : request.codeChallengeMethod());
        entity.setRequestedScope(request == null ? null : join(request.scopes()));
    }

    private void mapAuthorizationCode(
            OAuthAuthorizationCode code,
            DbIamOAuthAuthorization entity
    ) {
        if (code == null) {
            return;
        }
        entity.setAuthorizationCodeDigest(code.digest().value());
        entity.setAuthorizationCodeIssuedAt(code.period().issuedAt());
        entity.setAuthorizationCodeExpiresAt(code.period().expiresAt());
        entity.setAuthorizationCodeUsedAt(code.usedAt());
    }

    private void mapAccessToken(OAuthAccessToken token, DbIamOAuthAuthorization entity) {
        if (token == null) {
            return;
        }
        entity.setAccessTokenDigest(token.digest().value());
        entity.setAccessTokenIssuedAt(token.period().issuedAt());
        entity.setAccessTokenExpiresAt(token.period().expiresAt());
        entity.setAccessTokenClaims(token.claims());
    }

    private void mapRefreshToken(OAuthRefreshToken token, DbIamOAuthAuthorization entity) {
        if (token == null) {
            return;
        }
        entity.setRefreshTokenDigest(token.digest().value());
        entity.setRefreshTokenIssuedAt(token.period().issuedAt());
        entity.setRefreshTokenExpiresAt(token.period().expiresAt());
    }

    private void mapIdToken(OidcIdentityToken token, DbIamOAuthAuthorization entity) {
        if (token == null) {
            return;
        }
        entity.setIdTokenDigest(token.digest().value());
        entity.setIdTokenIssuedAt(token.period().issuedAt());
        entity.setIdTokenExpiresAt(token.period().expiresAt());
        entity.setIdTokenClaims(token.claims());
    }

    private Set<OAuthScope> scopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(" "))
                .filter(item -> !item.isBlank())
                .map(OAuthScope::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String join(Set<OAuthScope> scopes) {
        return scopes.stream()
                .map(OAuthScope::value)
                .sorted()
                .collect(Collectors.joining(" "));
    }
}
