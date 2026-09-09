package com.co.kc.imchat.management.iam.infrastructure.security.token;

import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamAuthorizationProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthJwkSourceTest {

    @Test
    void loadsDevelopmentRsaKeyPairFromClasspathPkcs12() throws Exception {
        IamAuthorizationProperties properties = new IamAuthorizationProperties(
                URI.create("http://localhost:18092"),
                "classpath:iam/iam-signing.p12",
                "ImChatIamLocalStore_2026!",
                "im-iam-signing",
                "ImChatIamLocalStore_2026!",
                Duration.ofHours(8),
                Duration.ofHours(24));

        OAuthJwkSource source = new OAuthJwkSource(properties, new DefaultResourceLoader());
        List<JWK> keys = source.get(
                new JWKSelector(new JWKMatcher.Builder().keyID("im-iam-signing").build()),
                null);

        assertThat(keys).singleElement().satisfies(key -> {
            assertThat(key.isPrivate()).isTrue();
            assertThat(key.size()).isGreaterThanOrEqualTo(2048);
        });
    }
}
