package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 应用角色标识。 */
public record ApplicationRoleId(Long value) {
    public ApplicationRoleId {
        AssertUtils.domainPropNotNull("role id must not be null", value);
        AssertUtils.domainPropTrue("role id must be positive", value > 0);
    }
}
