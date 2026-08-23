package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** Session 的身份版本。 */
public record SessionVersion(String value) {

    public SessionVersion {
        AssertUtils.domainPropNotBlank("version must not be blank", value);
    }
}
