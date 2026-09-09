package com.co.kc.imchat.management.admin.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：管理端设置的用户密码。
 */
public record ManagedUserPassword(String value) {
    public ManagedUserPassword {
        AssertUtils.domainPropNotBlank("password must not be blank", value);
    }
}
