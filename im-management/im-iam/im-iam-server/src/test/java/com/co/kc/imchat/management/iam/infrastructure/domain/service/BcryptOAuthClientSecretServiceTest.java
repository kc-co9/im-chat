package com.co.kc.imchat.management.iam.infrastructure.domain.service;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthRawClientSecret;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptOAuthClientSecretServiceTest {

    @Test
    void storesOnlyABcryptDigest() {
        OAuthRawClientSecret secret = new OAuthRawClientSecret("strong-client-secret-value");

        OAuthClientSecret digest = new BcryptOAuthClientSecretService().encode(secret);

        assertThat(digest.value()).startsWith("$2").doesNotContain(secret.value());
    }
}
