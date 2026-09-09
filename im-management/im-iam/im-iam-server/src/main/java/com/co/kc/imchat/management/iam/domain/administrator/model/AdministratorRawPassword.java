package com.co.kc.imchat.management.iam.domain.administrator.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 仅在认证或改密过程短暂存在的管理员明文密码。 */
public record AdministratorRawPassword(String value) {
    public AdministratorRawPassword {
        AssertUtils.domainPropNotBlank("administrator password must not be blank", value);
    }
}
