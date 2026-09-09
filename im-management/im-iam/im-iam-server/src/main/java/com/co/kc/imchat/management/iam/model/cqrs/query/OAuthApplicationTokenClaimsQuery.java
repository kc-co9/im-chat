package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 查询应用 Token 签发所需的可信 Claims。 */
public record OAuthApplicationTokenClaimsQuery(
        /* OAuth Client 标识。 */
        String clientId
) {
    public OAuthApplicationTokenClaimsQuery {
        AssertUtils.argNotBlank("oauth client id must not be blank", clientId);
    }
}
