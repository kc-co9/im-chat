package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** Refresh Token 的不可逆指纹，领域不解释其生成算法。 */
public record RefreshFingerprint(String value) {

    public RefreshFingerprint {
        AssertUtils.domainPropNotBlank("fingerprint must not be blank", value);
    }
}
