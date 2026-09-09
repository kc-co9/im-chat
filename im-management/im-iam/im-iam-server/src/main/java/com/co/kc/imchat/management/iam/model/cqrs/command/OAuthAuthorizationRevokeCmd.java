package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth 授权撤销命令。 */
public record OAuthAuthorizationRevokeCmd(String authorizationId) {
    public OAuthAuthorizationRevokeCmd {
        AssertUtils.argNotBlank("authorization id must not be blank", authorizationId);
    }
}
