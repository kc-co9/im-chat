package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 内部角色编码。 */
public record IamRoleCode(String value) {
    public IamRoleCode {
        AssertUtils.domainPropNotBlank("iam role code must not be blank", value);
    }
}
