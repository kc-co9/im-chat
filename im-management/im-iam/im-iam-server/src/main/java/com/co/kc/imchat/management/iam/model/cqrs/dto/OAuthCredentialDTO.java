package com.co.kc.imchat.management.iam.model.cqrs.dto;

import java.time.Instant;
import java.util.Map;

/** OAuth 凭据的应用层快照。 */
public record OAuthCredentialDTO(
        String digest,
        Instant issuedAt,
        Instant expiresAt,
        Map<String, Object> claims,
        Instant usedAt
) {
    public OAuthCredentialDTO {
        claims = claims == null ? Map.of() : Map.copyOf(claims);
    }
}
