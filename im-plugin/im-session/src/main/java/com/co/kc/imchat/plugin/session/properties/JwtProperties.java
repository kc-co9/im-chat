package com.co.kc.imchat.plugin.session.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "im.session.jwt")
@Getter
@Setter
public class JwtProperties implements InitializingBean {

    public static final int MINIMUM_SECRET_BYTES = 64;

    private boolean enabled;
    private String secret;
    private String issuer = "im-chat";
    private Duration accessTokenTtl = Duration.ofHours(2);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private Duration refreshThreshold = Duration.ofMinutes(15);

    @Override
    public void afterPropertiesSet() {
        if (enabled) {
            validate();
        }
    }

    public void validate() {
        AssertUtils.domainPropNotBlank("im.session.jwt.issuer must not be blank", issuer);
        requirePositive(accessTokenTtl, "access-token-ttl");
        requirePositive(refreshTokenTtl, "refresh-token-ttl");
        requirePositive(refreshThreshold, "refresh-threshold");
        AssertUtils.domainPropTrue(
                "access-token-ttl must be shorter than refresh-token-ttl",
                accessTokenTtl.compareTo(refreshTokenTtl) < 0);
        AssertUtils.domainPropTrue(
                "refresh-threshold must be shorter than access-token-ttl",
                refreshThreshold.compareTo(accessTokenTtl) < 0);
        AssertUtils.domainPropTrue(
                "im.session.jwt.secret must not be blank",
                hasSecret());
        AssertUtils.domainPropTrue(
                "im.session.jwt.secret must contain at least 64 UTF-8 bytes",
                secret.getBytes(StandardCharsets.UTF_8).length >= MINIMUM_SECRET_BYTES);
    }

    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }

    private void requirePositive(Duration value, String name) {
        String message = "im.session.jwt." + name + " must be positive";
        AssertUtils.domainPropNotNull(message, value);
        AssertUtils.domainPropTrue(message, !value.isZero() && !value.isNegative());
    }
}
