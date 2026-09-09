package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 内部角色业务标识。 */
public record IamRoleId(Long value) {
    public IamRoleId {
        AssertUtils.domainPropNotNull("iam role id must not be null", value);
        AssertUtils.domainPropTrue("iam role id must be positive", value > 0L);
    }
}
