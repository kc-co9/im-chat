package com.co.kc.imchat.management.iam.sdk.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/**
 * 管理应用接入 IAM 所需的服务、应用、OAuth 客户端和本地会话配置。
 */
@ConfigurationProperties("im.iam")
public record IamProperties(
        /* 是否启用 IAM SDK。 */
        Boolean enabled,
        /* IAM 服务的 Issuer。 */
        URI issuer,
        /* 当前接入 IAM 的管理应用。 */
        Application application,
        /* 访问 IAM 服务的 HTTP 配置。 */
        Http http,
        /* Token Introspection 配置。 */
        Introspection introspection
) {
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(8);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_STALE_TTL = Duration.ofMinutes(5);
    private static final Set<String> LOOPBACK_HOSTS =
            Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    public IamProperties {
        AssertUtils.argNotNull("IAM issuer must not be null", issuer);
        boolean httpsIssuer = "https".equalsIgnoreCase(issuer.getScheme())
                && issuer.getHost() != null;
        boolean localHttpIssuer = "http".equalsIgnoreCase(issuer.getScheme())
                && LOOPBACK_HOSTS.contains(issuer.getHost());
        AssertUtils.argTrue(
                "IAM issuer must use HTTPS outside loopback development",
                httpsIssuer || localHttpIssuer);
        AssertUtils.argNotNull("IAM application must not be null", application);
        http = http == null ? new Http(null, null) : http;
        introspection = introspection == null ? new Introspection(null) : introspection;
    }

    /** 当前接入 IAM 的管理应用及其客户端和会话配置。 */
    public record Application(
            /* 应用可识别编码。 */
            String key,
            /* 归属于当前应用的 OAuth 客户端。 */
            Clients clients,
            /* 当前应用的本地 BFF 会话配置。 */
            Session session
    ) {
        public Application {
            AssertUtils.argNotBlank("IAM application key must not be blank", key);
            AssertUtils.argNotNull("IAM application clients must not be null", clients);
            AssertUtils.argNotNull("IAM application session must not be null", session);
        }
    }

    /** 当前应用拥有的 OAuth 客户端集合。 */
    public record Clients(
            /* 浏览器登录和 Introspection 使用的客户端。 */
            WebClient web,
            /* 权限目录同步使用的机器客户端。 */
            CatalogClient catalog
    ) {
        public Clients {
            AssertUtils.argNotNull("IAM web client must not be null", web);
            catalog = catalog == null ? new CatalogClient(null, null) : catalog;
        }
    }

    /** 浏览器 BFF 使用的 OAuth 客户端。 */
    public record WebClient(
            /* OAuth Client ID。 */
            String clientId,
            /* OAuth Client Secret。 */
            String clientSecret,
            /* Authorization Code 回调地址。 */
            URI redirectUri,
            /* OIDC 登出后的回调地址。 */
            URI postLogoutRedirectUri
    ) {
        public WebClient {
            AssertUtils.argNotBlank("IAM web client id must not be blank", clientId);
            AssertUtils.argNotBlank("IAM web client secret must not be blank", clientSecret);
            AssertUtils.argNotNull("IAM redirect URI must not be null", redirectUri);
            AssertUtils.argNotNull(
                    "IAM post logout redirect URI must not be null",
                    postLogoutRedirectUri);
        }

        @Override
        public String toString() {
            return "WebClient[clientId=" + clientId
                    + ", clientSecret=[PROTECTED]"
                    + ", redirectUri=" + redirectUri
                    + ", postLogoutRedirectUri=" + postLogoutRedirectUri + "]";
        }
    }

    /** 权限目录同步使用的 OAuth 机器客户端。 */
    public record CatalogClient(
            /* OAuth Client ID。 */
            String clientId,
            /* OAuth Client Secret。 */
            String clientSecret
    ) {
        public CatalogClient {
            boolean hasClientId = clientId != null && !clientId.isBlank();
            boolean hasClientSecret = clientSecret != null && !clientSecret.isBlank();
            AssertUtils.argTrue(
                    "IAM catalog client id and secret must both be configured",
                    hasClientId == hasClientSecret);
        }

        public boolean configured() {
            return clientId != null && !clientId.isBlank();
        }

        @Override
        public String toString() {
            return "CatalogClient[clientId=" + clientId
                    + ", clientSecret=[PROTECTED]]";
        }
    }

    /** 当前应用保存在服务端的 BFF 会话配置。 */
    public record Session(
            /* Session 数据加密密钥。 */
            String encryptionKey,
            /* 浏览器 Session Cookie 名称。 */
            String cookieName,
            /* 是否只允许 HTTPS 发送 Cookie。 */
            Boolean secureCookie,
            /* Cookie SameSite 策略。 */
            String sameSite,
            /* Session 有效期。 */
            Duration ttl
    ) {
        public Session {
            AssertUtils.argNotBlank(
                    "IAM Session encryption key must not be blank",
                    encryptionKey);
            AssertUtils.argTrue(
                    "IAM Session encryption key must decode to 32 bytes",
                    decodeKey(encryptionKey).length == 32);
            cookieName = defaultText(cookieName, "IM_IAM_SESSION");
            sameSite = defaultText(sameSite, "Lax");
            ttl = defaultDuration(ttl, DEFAULT_SESSION_TTL);
        }

        public byte[] encryptionKeyBytes() {
            return decodeKey(encryptionKey);
        }

        public boolean secureCookieEnabled() {
            return !Boolean.FALSE.equals(secureCookie);
        }

        @Override
        public String toString() {
            return "Session[encryptionKey=[PROTECTED]"
                    + ", cookieName=" + cookieName
                    + ", secureCookie=" + secureCookie
                    + ", sameSite=" + sameSite
                    + ", ttl=" + ttl + "]";
        }
    }

    /** IAM HTTP 调用超时配置。 */
    public record Http(
            /* 建立连接的超时时间。 */
            Duration connectTimeout,
            /* 等待响应的超时时间。 */
            Duration readTimeout
    ) {
        public Http {
            connectTimeout = defaultDuration(connectTimeout, DEFAULT_CONNECT_TIMEOUT);
            readTimeout = defaultDuration(readTimeout, DEFAULT_READ_TIMEOUT);
        }
    }

    /** Token Introspection 可用性配置。 */
    public record Introspection(
            /* IAM 暂时不可用时允许使用缓存结果的最长时间。 */
            Duration staleTtl
    ) {
        public Introspection {
            staleTtl = defaultDuration(staleTtl, DEFAULT_STALE_TTL);
            AssertUtils.argTrue(
                    "IAM stale cache TTL must not exceed five minutes",
                    staleTtl.compareTo(DEFAULT_STALE_TTL) <= 0);
        }
    }

    private static byte[] decodeKey(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "IAM Session encryption key must use Base64",
                    exception);
        }
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Duration defaultDuration(Duration value, Duration defaultValue) {
        Duration result = value == null ? defaultValue : value;
        AssertUtils.argTrue(
                "IAM duration must be positive",
                !result.isZero() && !result.isNegative());
        return result;
    }
}
