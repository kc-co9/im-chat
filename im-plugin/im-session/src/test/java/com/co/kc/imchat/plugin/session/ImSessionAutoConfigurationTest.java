package com.co.kc.imchat.plugin.session;

import com.co.kc.imchat.plugin.session.properties.JwtProperties;
import com.co.kc.imchat.plugin.session.properties.SessionWebProperties;
import com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec;
import com.co.kc.imchat.plugin.session.web.UserContextInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ImSessionAutoConfigurationTest {

    private static final String STRONG_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ImSessionAutoConfiguration.class);

    private final WebApplicationContextRunner webContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ImSessionAutoConfiguration.class));

    @Test
    void doesNotCreateCodecWhileJwtIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(JwtTokenCodec.class);
            assertThat(context).doesNotHaveBean(SessionWebProperties.class);
            assertThat(context).doesNotHaveBean(UserContextInterceptor.class);
        });
    }

    @Test
    void registersServletSessionAdapterFromSingleAutoConfigurationEntry() {
        webContextRunner
                .withPropertyValues("im.session.web.public-paths[0]=/user/signIn")
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionWebProperties.class);
                    assertThat(context).hasSingleBean(UserContextInterceptor.class);
                    assertThat(context).hasBean("sessionWebMvcConfigurer");
                });
    }

    @Test
    void bindsJwtDefaultsWhenCodecIsEnabled() {
        contextRunner.withPropertyValues(
                "im.session.jwt.enabled=true",
                "im.session.jwt.secret=" + STRONG_SECRET)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtTokenCodec.class);
                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.getAccessTokenTtl()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.getRefreshTokenTtl()).isEqualTo(Duration.ofDays(30));
                    assertThat(properties.getRefreshThreshold()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void rejectsMissingSecretInLocalProfile() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=local",
                "im.session.jwt.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingSecretOutsideLocalProfile() {
        contextRunner.withPropertyValues("im.session.jwt.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsWeakExternalSecret() {
        contextRunner.withPropertyValues(
                "im.session.jwt.enabled=true",
                "im.session.jwt.secret=too-short")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidIssuerAndTtlOrdering() {
        contextRunner.withPropertyValues(
                "im.session.jwt.enabled=true",
                "im.session.jwt.secret=" + STRONG_SECRET,
                "im.session.jwt.issuer= ",
                "im.session.jwt.access-token-ttl=31d",
                "im.session.jwt.refresh-token-ttl=30d")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void backsOffWhenApplicationProvidesCodec() {
        JwtTokenCodec customCodec = new JwtTokenCodec(
                new SecretKeySpec(STRONG_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"),
                "custom",
                Duration.ofHours(1),
                Duration.ofDays(1),
                Clock.systemUTC());
        contextRunner.withBean(JwtTokenCodec.class, () -> customCodec)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtTokenCodec.class);
                    assertThat(context.getBean(JwtTokenCodec.class)).isSameAs(customCodec);
                });
    }
}
