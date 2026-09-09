package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 角色显示名称。 */
public record ApplicationRoleName(String value) {
    public ApplicationRoleName {
        AssertUtils.domainPropNotBlank("role name must not be blank", value);
    }
}
