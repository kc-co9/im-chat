package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 编码后的 Refresh Token。 */
public record RefreshToken(String value) {

    public RefreshToken {
        AssertUtils.domainPropNotBlank("refreshToken must not be blank", value);
    }
}
