package com.co.kc.imchat.management.iam.model.io;

/** OAuth 在线授权会话 HTTP 响应。 */
public record OAuthSessionResponse(
        String id,
        Long administratorId,
        String username,
        Long createdAt,
        Long lastAccessAt,
        Long expiresAt
) {
}
