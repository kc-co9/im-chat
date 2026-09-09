package com.co.kc.imchat.management.iam.sdk.session.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmIamSessionCipherTest {

    @Test
    void encryptsWithFreshNonceAndRejectsTampering() {
        byte[] key = Arrays.copyOf(
                "test-only-iam-session-key-material".getBytes(StandardCharsets.UTF_8), 32);
        AesGcmIamSessionCipher cipher = new AesGcmIamSessionCipher(key);

        String first = cipher.encrypt("opaque-access-token");
        String second = cipher.encrypt("opaque-access-token");

        assertThat(first).isNotEqualTo(second).doesNotContain("opaque-access-token");
        assertThat(cipher.decrypt(first)).isEqualTo("opaque-access-token");
        byte[] tamperedBytes = Base64.getUrlDecoder().decode(first);
        tamperedBytes[tamperedBytes.length - 1] ^= 1;
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedBytes);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
