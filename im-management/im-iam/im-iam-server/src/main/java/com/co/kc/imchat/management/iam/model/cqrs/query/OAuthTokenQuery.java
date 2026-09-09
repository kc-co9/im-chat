package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialType;

/** 按 Token 摘要查询 OAuth 授权。 */
public record OAuthTokenQuery(
        /* Token 不可逆摘要。 */
        String tokenDigest,
        /* Token 类型；为空时搜索全部当前凭据。 */
        OAuthCredentialType credentialType
) {
    public OAuthTokenQuery {
        AssertUtils.argNotBlank("token digest must not be blank", tokenDigest);
    }
}
