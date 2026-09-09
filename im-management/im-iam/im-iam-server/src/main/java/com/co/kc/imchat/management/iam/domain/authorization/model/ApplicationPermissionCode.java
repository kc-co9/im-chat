package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 应用定义的稳定权限编码。 */
public record ApplicationPermissionCode(String value) {
    public ApplicationPermissionCode {
        AssertUtils.domainPropNotBlank("permission code must not be blank", value);
    }
}
