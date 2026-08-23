package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 建立认证会话后的领域结果，包含新凭证以及被替换的旧会话版本。 */
public record SessionEstablishment(
        CredentialPair credentials,
        SessionVersion replacedVersion
) {
    public SessionEstablishment {
        AssertUtils.domainPropNotNull("credentials must not be null", credentials);
    }
}
