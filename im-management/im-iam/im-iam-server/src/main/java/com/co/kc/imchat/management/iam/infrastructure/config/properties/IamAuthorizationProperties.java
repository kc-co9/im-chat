package com.co.kc.imchat.management.iam.infrastructure.config.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/** IAM OAuth2/OIDC 端点与外部签名密钥库配置。 */
@ConfigurationProperties("im.iam.authorization")
public record IamAuthorizationProperties(
        URI issuer,
        String keyStoreLocation,
        String keyStorePassword,
        String keyAlias,
        String keyPassword,
        Duration ssoIdleTimeout,
        Duration ssoAbsoluteTimeout
) {
    private static final Duration DEFAULT_SSO_IDLE_TIMEOUT = Duration.ofHours(8);
    private static final Duration DEFAULT_SSO_ABSOLUTE_TIMEOUT = Duration.ofHours(24);

    public IamAuthorizationProperties {
        AssertUtils.argNotNull("IAM issuer must not be null", issuer);
        boolean httpsIssuer = "https".equalsIgnoreCase(issuer.getScheme());
        boolean localHttpIssuer = "http".equalsIgnoreCase(issuer.getScheme())
                && Set.of("localhost", "127.0.0.1", "::1", "[::1]")
                .contains(issuer.getHost());
        AssertUtils.argTrue("IAM issuer must use HTTPS outside loopback development",
                httpsIssuer || localHttpIssuer);
        AssertUtils.argNotBlank("IAM key store location must not be blank", keyStoreLocation);
        AssertUtils.argTrue("IAM signing key store must use file: or classpath:",
                keyStoreLocation.startsWith("file:")
                        || keyStoreLocation.startsWith("classpath:"));
        AssertUtils.argNotBlank("IAM key store password must not be blank", keyStorePassword);
        AssertUtils.argNotBlank("IAM key alias must not be blank", keyAlias);
        AssertUtils.argNotBlank("IAM key password must not be blank", keyPassword);
        ssoIdleTimeout = ssoIdleTimeout == null ? DEFAULT_SSO_IDLE_TIMEOUT : ssoIdleTimeout;
        ssoAbsoluteTimeout = ssoAbsoluteTimeout == null
                ? DEFAULT_SSO_ABSOLUTE_TIMEOUT : ssoAbsoluteTimeout;
        AssertUtils.argTrue("IAM SSO idle timeout must be positive",
                !ssoIdleTimeout.isNegative() && !ssoIdleTimeout.isZero());
        AssertUtils.argTrue("IAM SSO absolute timeout must exceed idle timeout",
                ssoAbsoluteTimeout.compareTo(ssoIdleTimeout) > 0);
    }
}
