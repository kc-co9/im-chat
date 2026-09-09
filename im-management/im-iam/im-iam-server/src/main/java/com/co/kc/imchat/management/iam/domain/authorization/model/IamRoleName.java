package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 内部角色名称。 */
public record IamRoleName(String value) {
    public IamRoleName {
        AssertUtils.domainPropNotBlank("iam role name must not be blank", value);
    }
}
