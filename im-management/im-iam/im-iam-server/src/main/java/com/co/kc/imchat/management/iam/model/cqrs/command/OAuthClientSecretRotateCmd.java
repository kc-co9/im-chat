package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 轮换 IAM OAuth 客户端密钥命令。 */
public record OAuthClientSecretRotateCmd(String clientId, String clientSecret) {
    public OAuthClientSecretRotateCmd {
        AssertUtils.argNotBlank("oauth client id must not be blank", clientId);
        AssertUtils.argNotBlank("client secret must not be blank", clientSecret);
    }
}
