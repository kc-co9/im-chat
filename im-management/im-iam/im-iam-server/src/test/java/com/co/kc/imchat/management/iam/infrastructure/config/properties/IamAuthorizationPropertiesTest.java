package com.co.kc.imchat.management.iam.infrastructure.config.properties;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamAuthorizationPropertiesTest {

    @Test
    void acceptsExternalKeyStoreAndAppliesSsoDefaults() {
        IamAuthorizationProperties properties = new IamAuthorizationProperties(
                URI.create("https://iam.example.com"),
                "file:/etc/im-chat/iam-signing.p12",
                "store-password",
                "iam-signing",
                "key-password",
                null,
                null);

        assertThat(properties.ssoIdleTimeout()).isEqualTo(Duration.ofHours(8));
        assertThat(properties.ssoAbsoluteTimeout()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void acceptsClasspathKeyStoreAndLoopbackHttpIssuer() {
        IamAuthorizationProperties properties = new IamAuthorizationProperties(
                URI.create("http://localhost:18090"),
                "classpath:iam-signing.p12",
                "store-password",
                "iam-signing",
                "key-password",
                Duration.ofHours(8),
                Duration.ofHours(24));

        assertThat(properties.keyStoreLocation()).isEqualTo("classpath:iam-signing.p12");
    }

    @Test
    void rejectsHttpIssuerOutsideLoopback() {
        assertThatThrownBy(() -> new IamAuthorizationProperties(
                URI.create("http://iam.example.com"),
                "file:/etc/im-chat/iam-signing.p12",
                "store-password",
                "iam-signing",
                "key-password",
                Duration.ofHours(8),
                Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsAbsoluteTimeoutThatDoesNotExceedIdleTimeout() {
        assertThatThrownBy(() -> new IamAuthorizationProperties(
                URI.create("https://iam.example.com"),
                "file:/etc/im-chat/iam-signing.p12",
                "store-password",
                "iam-signing",
                "key-password",
                Duration.ofHours(8),
                Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute timeout");
    }
}
