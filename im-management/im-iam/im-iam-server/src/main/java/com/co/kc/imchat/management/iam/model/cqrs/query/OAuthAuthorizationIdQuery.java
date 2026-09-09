package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 按授权标识查询 OAuth 授权。 */
public record OAuthAuthorizationIdQuery(
        /* OAuth 授权业务标识。 */
        String authorizationId
) {
    public OAuthAuthorizationIdQuery {
        AssertUtils.argNotBlank("authorization id must not be blank", authorizationId);
    }
}
