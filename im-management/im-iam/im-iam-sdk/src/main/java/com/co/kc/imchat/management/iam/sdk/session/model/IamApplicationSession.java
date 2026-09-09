package com.co.kc.imchat.management.iam.sdk.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;

import java.time.Instant;

/** 管理应用 BFF 会话；浏览器仅持有随机会话标识。 */
public record IamApplicationSession(
        String sessionId,
        IamTokenSet tokens,
        String csrfToken,
        Instant createdAt,
        Instant updatedAt
) {
    public IamApplicationSession {
        AssertUtils.argNotBlank("IAM application Session ID must not be blank", sessionId);
        AssertUtils.argNotNull("IAM application Token set must not be null", tokens);
        AssertUtils.argNotBlank("IAM CSRF Token must not be blank", csrfToken);
        AssertUtils.allArgNotNull(
                "IAM application Session time must not be null", createdAt, updatedAt);
    }

    public IamApplicationSession rotate(IamTokenSet newTokens, Instant rotatedAt) {
        return new IamApplicationSession(sessionId, newTokens, csrfToken, createdAt, rotatedAt);
    }

    public IamApplicationSession withCsrfToken(String newCsrfToken, Instant updatedAt) {
        return new IamApplicationSession(sessionId, tokens, newCsrfToken, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "IamApplicationSession[sessionId=" + sessionId
                + ", tokens=<redacted>, csrfToken=<redacted>, createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "]";
    }
}
