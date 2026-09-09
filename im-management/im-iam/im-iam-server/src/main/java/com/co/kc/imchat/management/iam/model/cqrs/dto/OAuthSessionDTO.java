package com.co.kc.imchat.management.iam.model.cqrs.dto;

import java.time.Instant;

/** OAuth 在线授权会话查询结果。 */
public record OAuthSessionDTO(
        String id,
        Long administratorId,
        String username,
        Instant createdAt,
        Instant lastAccessAt,
        Instant expiresAt
) {
}
