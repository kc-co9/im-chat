package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 内部管理权限编码。 */
public record IamPermissionCode(String value) {
    public IamPermissionCode {
        AssertUtils.domainPropNotBlank("iam permission code must not be blank", value);
    }
}
