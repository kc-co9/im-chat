package com.co.kc.imchat.management.iam.domain.administrator.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 仅保存管理员密码摘要。 */
public record AdministratorPassword(String value) {
    public AdministratorPassword {
        AssertUtils.domainPropNotBlank("administrator password hash must not be blank", value);
    }
}
