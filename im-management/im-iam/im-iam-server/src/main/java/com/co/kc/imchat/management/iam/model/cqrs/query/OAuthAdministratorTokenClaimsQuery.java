package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 查询管理员 Token 签发所需的可信 Claims。 */
public record OAuthAdministratorTokenClaimsQuery(
        /* OAuth Client 标识。 */
        String clientId,
        /* 管理员业务 ID。 */
        Long administratorId
) {
    public OAuthAdministratorTokenClaimsQuery {
        AssertUtils.argNotBlank("oauth client id must not be blank", clientId);
        AssertUtils.argNotNull("administrator id must not be null", administratorId);
    }
}
