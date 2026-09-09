package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 应用范围内稳定的角色编码。 */
public record ApplicationRoleCode(String value) {
    public ApplicationRoleCode {
        AssertUtils.domainPropNotBlank("role code must not be blank", value);
    }
}
