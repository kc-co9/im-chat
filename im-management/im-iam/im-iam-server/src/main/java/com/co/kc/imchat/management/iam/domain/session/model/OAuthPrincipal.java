package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** OAuth 授权主体，由主体类型和稳定业务标识组成。 */
public record OAuthPrincipal(
        /* 主体类别。 */
        OAuthPrincipalType type,
        /* 主体的稳定业务标识。 */
        String value
) {
    public OAuthPrincipal {
        AssertUtils.domainPropNotNull("OAuth principal type must not be null", type);
        AssertUtils.domainPropNotBlank("OAuth principal value must not be blank", value);
    }
}
