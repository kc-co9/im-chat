package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 停用 IAM OAuth 客户端命令。 */
public record OAuthClientDisableCmd(String clientId) {
    public OAuthClientDisableCmd {
        AssertUtils.argNotBlank("oauth client id must not be blank", clientId);
    }
}
