package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 权限标识。 */
public record ApplicationPermissionId(Long value) {
    public ApplicationPermissionId {
        AssertUtils.domainPropNotNull("permission id must not be null", value);
        AssertUtils.domainPropTrue("permission id must be positive", value > 0);
    }
}
