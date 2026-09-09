package com.co.kc.imchat.management.iam.domain.administrator.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 管理员用户名。 */
public record AdministratorUsername(String value) {
    public AdministratorUsername {
        AssertUtils.domainPropNotBlank("administrator username must not be blank", value);
    }
}
