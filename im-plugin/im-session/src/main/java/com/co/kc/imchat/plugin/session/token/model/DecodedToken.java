package com.co.kc.imchat.plugin.session.token.model;

import java.time.Instant;
import java.util.Objects;

public record DecodedToken(
        long userId,
        String sessionVersion,
        TokenType tokenType,
        String tokenId,
        Instant issuedAt,
        Instant expiresAt) {

    public DecodedToken {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionVersion == null || sessionVersion.isBlank()) {
            throw new IllegalArgumentException("sessionVersion must not be blank");
        }
        Objects.requireNonNull(tokenType, "tokenType must not be null");
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("tokenId must not be blank");
        }
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
