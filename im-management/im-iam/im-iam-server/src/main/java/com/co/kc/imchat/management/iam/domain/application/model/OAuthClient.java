package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/** 应用接入 IAM 使用的 OAuth 客户端聚合根。 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class OAuthClient extends Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private OAuthClientId clientId;
    private AppId appId;
    private AppId audienceAppId;
    private OAuthClientName name;
    private OAuthClientSecret clientSecret;
    private Set<OAuthGrantType> grantTypes;
    private Set<OAuthScope> scopes;
    private Set<RedirectUri> redirectUris;
    private Set<RedirectUri> postLogoutRedirectUris;
    private OAuthClientStatus status;

    private OAuthClient() {
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull(
                "oauth client required properties must not be null",
                clientId, appId, audienceAppId, name, clientSecret, grantTypes, scopes,
                redirectUris, postLogoutRedirectUris, status);
        AssertUtils.domainPropNotEmpty("oauth client grant types must not be empty", grantTypes);
        AssertUtils.domainPropNotEmpty("oauth client scopes must not be empty", scopes);
        validateAccess(scopes, redirectUris, postLogoutRedirectUris);
        grantTypes = Set.copyOf(grantTypes);
        scopes = Set.copyOf(scopes);
        redirectUris = Set.copyOf(redirectUris);
        postLogoutRedirectUris = Set.copyOf(postLogoutRedirectUris);
    }

    private void validateAccess(
            Set<OAuthScope> scopes,
            Set<RedirectUri> redirectUris,
            Set<RedirectUri> postLogoutRedirectUris
    ) {
        AssertUtils.domainPropNotEmpty("oauth client scopes must not be empty", scopes);
        boolean browserClient = grantTypes.contains(OAuthGrantType.AUTHORIZATION_CODE);
        boolean machineClient = grantTypes.contains(OAuthGrantType.CLIENT_CREDENTIALS);
        if (browserClient && machineClient) {
            throw new IllegalStateException(
                    "oauth client must not mix browser and machine grant types");
        }
        if (browserClient) {
            AssertUtils.domainPropNotEmpty("browser client redirect URIs must not be empty", redirectUris);
        } else if (!machineClient || grantTypes.size() != 1) {
            throw new IllegalStateException(
                    "oauth client must use authorization code or client credentials");
        } else if (!redirectUris.isEmpty() || !postLogoutRedirectUris.isEmpty()) {
            throw new IllegalStateException("client credentials client must not declare redirect URIs");
        }
    }

    /** 创建并校验 OAuth 客户端。 */
    public static final class Builder {
        private final OAuthClient client = new OAuthClient();

        public Builder clientId(OAuthClientId clientId) {
            client.clientId = clientId;
            return this;
        }

        public Builder appId(AppId appId) {
            client.appId = appId;
            return this;
        }

        public Builder audienceAppId(AppId audienceAppId) {
            client.audienceAppId = audienceAppId;
            return this;
        }

        public Builder name(OAuthClientName name) {
            client.name = name;
            return this;
        }

        public Builder clientSecret(OAuthClientSecret clientSecret) {
            client.clientSecret = clientSecret;
            return this;
        }

        public Builder grantTypes(Set<OAuthGrantType> grantTypes) {
            client.grantTypes = grantTypes;
            return this;
        }

        public Builder scopes(Set<OAuthScope> scopes) {
            client.scopes = scopes;
            return this;
        }

        public Builder redirectUris(Set<RedirectUri> redirectUris) {
            client.redirectUris = redirectUris;
            return this;
        }

        public Builder postLogoutRedirectUris(Set<RedirectUri> redirectUris) {
            client.postLogoutRedirectUris = redirectUris;
            return this;
        }

        public Builder status(OAuthClientStatus status) {
            client.status = status;
            return this;
        }

        public OAuthClient build() {
            client.validate();
            return client;
        }
    }

    public boolean allowsRedirect(RedirectUri redirectUri) {
        return redirectUris.contains(redirectUri);
    }

    public boolean allowsPostLogoutRedirect(RedirectUri redirectUri) {
        return postLogoutRedirectUris.contains(redirectUri);
    }

    /** 判断 OAuth 客户端当前是否可用。 */
    public boolean isActive() {
        return status == OAuthClientStatus.ACTIVE;
    }

    /**
     * 判断 OAuth 客户端是否属于指定应用。
     *
     * @param applicationId 应用标识
     * @return 是否属于该应用
     */
    public boolean belongsTo(AppId applicationId) {
        return appId.equals(applicationId);
    }

    /** 使用已加密的新密钥摘要替换当前密钥。 */
    public void rotateSecret(OAuthClientSecret secret) {
        AssertUtils.domainPropNotNull("stored client secret must not be null", secret);
        if (status != OAuthClientStatus.ACTIVE) {
            throw new TransitionException("已停用 OAuth 客户端不能轮换密钥");
        }
        clientSecret = secret;
    }

    /** 修改客户端允许的 Scope 与回调地址。 */
    public void reviseAccess(
            Set<OAuthScope> scopes,
            Set<RedirectUri> redirectUris,
            Set<RedirectUri> postLogoutRedirectUris
    ) {
        if (status != OAuthClientStatus.ACTIVE) {
            throw new TransitionException("已停用 OAuth 客户端不能修改访问配置");
        }
        validateAccess(scopes, redirectUris, postLogoutRedirectUris);
        this.scopes = Set.copyOf(scopes);
        this.redirectUris = Set.copyOf(redirectUris);
        this.postLogoutRedirectUris = Set.copyOf(postLogoutRedirectUris);
    }

    /** 停用 OAuth 客户端。 */
    public void disable() {
        if (status != OAuthClientStatus.ACTIVE) {
            throw new TransitionException("只有启用中的 OAuth 客户端可以停用");
        }
        status = OAuthClientStatus.DISABLED;
    }
}
