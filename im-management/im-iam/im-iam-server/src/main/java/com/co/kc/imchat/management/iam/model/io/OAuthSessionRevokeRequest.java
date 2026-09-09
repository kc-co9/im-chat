package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth 会话撤销请求。 */
public record OAuthSessionRevokeRequest(String sessionId) {
    public OAuthSessionRevokeRequest {
        AssertUtils.argNotBlank("sessionId must not be blank", sessionId);
    }
}
