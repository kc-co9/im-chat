package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 查询 Spring Authorization Server 所需的 OAuth Client 注册信息。 */
public record OAuthClientRegistrationQuery(
        /* OAuth Client 标识。 */
        String clientId
) {
    public OAuthClientRegistrationQuery {
        AssertUtils.argNotBlank("oauth client id must not be blank", clientId);
    }
}
