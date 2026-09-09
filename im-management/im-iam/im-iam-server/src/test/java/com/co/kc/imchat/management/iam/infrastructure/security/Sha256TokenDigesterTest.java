package com.co.kc.imchat.management.iam.infrastructure.security;

import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256OAuthTokenDigesterTest {

    @Test
    void producesStableHexDigestWithoutRetainingRawToken() {
        Sha256OAuthTokenDigester digester = new Sha256OAuthTokenDigester();

        String digest = digester.digest("opaque-token-value");

        assertThat(digest).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(digest).doesNotContain("opaque-token-value");
        assertThat(digester.matches("opaque-token-value", digest)).isTrue();
        assertThat(digester.matches("another-token", digest)).isFalse();
    }
}
