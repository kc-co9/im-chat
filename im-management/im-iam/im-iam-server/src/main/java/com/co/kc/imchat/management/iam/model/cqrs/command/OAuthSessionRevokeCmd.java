package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 撤销 OAuth 在线会话命令。 */
public record OAuthSessionRevokeCmd(String sessionId) {
    public OAuthSessionRevokeCmd {
        AssertUtils.argNotBlank("sessionId must not be blank", sessionId);
    }
}
