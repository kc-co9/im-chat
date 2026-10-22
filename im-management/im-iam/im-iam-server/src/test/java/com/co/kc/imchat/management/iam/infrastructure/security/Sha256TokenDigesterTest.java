package com.co.kc.imchat.management.iam.infrastructure.security;

import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sha256TokenDigesterTest {

    @Test
    void malformedHexDigestDoesNotMatchToken() {
        Sha256OAuthTokenDigester digester = new Sha256OAuthTokenDigester();

        assertThat(digester.matches("opaque-token-value", "not-hex")).isFalse();
        assertThat(digester.matches("opaque-token-value", "0")).isFalse();
    }

    @Test
    void blankTokenAndDigestAreRejected() {
        Sha256OAuthTokenDigester digester = new Sha256OAuthTokenDigester();

        assertThatThrownBy(() -> digester.digest(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("token must not be blank");
        assertThatThrownBy(() -> digester.digest(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("token must not be blank");
        assertThatThrownBy(() -> digester.matches(" ", "00"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("token must not be blank");
        assertThatThrownBy(() -> digester.matches("opaque-token-value", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("token digest must not be blank");
    }

    @Test
    void producesStableHexDigestWithoutRetainingRawToken() {
        Sha256OAuthTokenDigester digester = new Sha256OAuthTokenDigester();

        String digest = digester.digest("opaque-token-value");

        assertThat(digest).isEqualTo("0fdde61b3874c8ae97ab0b89a3821f7a81b9423f7686de6c98fcea9b3c16982d");
        assertThat(digest).doesNotContain("opaque-token-value");
        assertThat(digester.matches("opaque-token-value", digest)).isTrue();
        assertThat(digester.matches("another-token", digest)).isFalse();
    }
}
