package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.domain.shared.model.Validator;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * OAuth 协议授权及其当前凭据状态聚合根。
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class OAuthAuthorization extends Identification implements Validator {
    /* OAuth 授权的稳定业务标识。 */
    private OAuthAuthorizationId id;
    /* 授权所属应用。 */
    private AppId appId;
    /* 参与授权的 OAuth 客户端。 */
    private OAuthClientId clientId;
    /* 授权主体及其稳定标识。 */
    private OAuthPrincipal principal;
    /* OAuth 授权方式。 */
    private OAuthGrantType grantType;
    /* 浏览器授权请求状态。 */
    private OAuthAuthorizationRequest request;
    /* 当前授权码状态。 */
    private OAuthAuthorizationCode authorizationCode;
    /* 当前 Access Token 状态。 */
    private OAuthAccessToken accessToken;
    /* 当前 Refresh Token 状态。 */
    private OAuthRefreshToken refreshToken;
    /* 当前 OIDC ID Token 状态。 */
    private OidcIdentityToken idToken;
    /* 授权范围。 */
    private Set<OAuthScope> scopes;
    /* 授权生命周期状态。 */
    private OAuthAuthorizationStatus status;
    /* 授权撤销时间。 */
    private Instant revokedAt;

    private OAuthAuthorization() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 撤销当前授权族中的全部凭据。
     */
    public void revoke(Instant revokedAt) {
        AssertUtils.domainPropNotNull("OAuth authorization revocation time must not be null", revokedAt);
        status = OAuthAuthorizationStatus.REVOKED;
        this.revokedAt = revokedAt;
    }

    /**
     * 更新浏览器授权请求状态。
     */
    public void changeRequest(OAuthAuthorizationRequest request) {
        if (grantType == OAuthGrantType.AUTHORIZATION_CODE) {
            AssertUtils.domainPropNotNull(
                    "browser OAuth authorization requires request",
                    request);
        }
        this.request = request;
    }

    /**
     * 更新授权码状态并保留已经发生的消费事实。
     */
    public void changeAuthorizationCode(OAuthAuthorizationCode authorizationCode) {
        if (this.authorizationCode == null
                || !this.authorizationCode.isConsumed()
                || authorizationCode == null) {
            this.authorizationCode = authorizationCode;
            return;
        }
        this.authorizationCode = new OAuthAuthorizationCode(
                authorizationCode.digest(),
                authorizationCode.period(),
                this.authorizationCode.usedAt());
    }

    /**
     * 更新当前 Access Token 状态。
     */
    public void changeAccessToken(OAuthAccessToken accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * 更新当前 Refresh Token 状态。
     */
    public void changeRefreshToken(OAuthRefreshToken refreshToken) {
        this.refreshToken = refreshToken;
    }

    /**
     * 更新当前 OIDC ID Token 状态。
     */
    public void changeIdToken(OidcIdentityToken idToken) {
        this.idToken = idToken;
    }

    /**
     * 更新当前授权范围。
     */
    public void changeScopes(Set<OAuthScope> scopes) {
        AssertUtils.domainPropNotEmpty("OAuth authorization scopes must not be empty", scopes);
        this.scopes = Set.copyOf(scopes);
    }

    /**
     * 判断当前授权是否已经撤销。
     */
    public boolean isRevoked() {
        return status == OAuthAuthorizationStatus.REVOKED;
    }

    /** 判断当前授权是否接受给定凭据。 */
    public boolean acceptsCredential(
            OAuthCredentialDigest credentialDigest,
            OAuthCredentialType credentialType
    ) {
        if (isRevoked()) {
            return false;
        }
        return credentialType != OAuthCredentialType.REFRESH_TOKEN
                || matchesRefreshToken(credentialDigest);
    }

    private boolean matchesRefreshToken(OAuthCredentialDigest refreshTokenDigest) {
        return Objects.equals(refreshToken.digest(), refreshTokenDigest);
    }

    @Override
    public void validate() {
        AssertUtils.allDomainPropNotNull(
                "OAuth authorization required properties must not be null",
                id, appId, clientId, principal, grantType, scopes, status);
        AssertUtils.domainPropNotEmpty("OAuth authorization scopes must not be empty", scopes);
        if (grantType == OAuthGrantType.AUTHORIZATION_CODE) {
            AssertUtils.domainPropTrue(
                    "browser OAuth authorization requires administrator principal",
                    principal.type() == OAuthPrincipalType.ADMINISTRATOR);
            AssertUtils.domainPropNotNull(
                    "browser OAuth authorization requires request", request);
        }
        if (status == OAuthAuthorizationStatus.REVOKED) {
            AssertUtils.domainPropNotNull("revoked OAuth authorization requires revocation time", revokedAt);
        } else {
            AssertUtils.domainPropTrue(
                    "active or expired OAuth authorization must not have revocation time",
                    revokedAt == null);
        }
        scopes = Set.copyOf(scopes);
    }

    /**
     * 创建并校验 OAuth 授权聚合。
     */
    public static final class Builder {
        private final OAuthAuthorization authorization = new OAuthAuthorization();

        public Builder id(OAuthAuthorizationId id) {
            authorization.id = id;
            return this;
        }

        public Builder appId(AppId appId) {
            authorization.appId = appId;
            return this;
        }

        public Builder clientId(OAuthClientId clientId) {
            authorization.clientId = clientId;
            return this;
        }

        public Builder principal(OAuthPrincipal principal) {
            authorization.principal = principal;
            return this;
        }

        public Builder grantType(OAuthGrantType grantType) {
            authorization.grantType = grantType;
            return this;
        }

        public Builder request(OAuthAuthorizationRequest request) {
            authorization.request = request;
            return this;
        }

        public Builder authorizationCode(OAuthAuthorizationCode authorizationCode) {
            authorization.authorizationCode = authorizationCode;
            return this;
        }

        public Builder accessToken(OAuthAccessToken accessToken) {
            authorization.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(OAuthRefreshToken refreshToken) {
            authorization.refreshToken = refreshToken;
            return this;
        }

        public Builder idToken(OidcIdentityToken idToken) {
            authorization.idToken = idToken;
            return this;
        }

        public Builder scopes(Set<OAuthScope> scopes) {
            authorization.scopes = scopes;
            return this;
        }

        public Builder status(OAuthAuthorizationStatus status) {
            authorization.status = status;
            return this;
        }

        public Builder revokedAt(Instant revokedAt) {
            authorization.revokedAt = revokedAt;
            return this;
        }

        public OAuthAuthorization build() {
            authorization.validate();
            return authorization;
        }
    }
}
