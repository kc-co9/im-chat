package com.co.kc.imchat.service.account.infrastructure.security;

import com.co.kc.imchat.plugin.session.properties.JwtProperties;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Creates the HMAC key used to fingerprint refresh credentials.
 */
public final class JwtFingerprintKeyFactory {
    private static final int EPHEMERAL_KEY_BYTES = 32;

    private final SecureRandom secureRandom;

    public JwtFingerprintKeyFactory() {
        this.secureRandom = new SecureRandom();
    }

    public byte[] create(JwtProperties jwtProperties) {
        if (jwtProperties.hasSecret()) {
            return jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        byte[] key = new byte[EPHEMERAL_KEY_BYTES];
        secureRandom.nextBytes(key);
        return key;
    }
}
