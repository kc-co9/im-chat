package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 编码后的 Access Token。 */
public record AccessToken(String value) {

    public AccessToken {
        AssertUtils.domainPropNotBlank("accessToken must not be blank", value);
    }
}
