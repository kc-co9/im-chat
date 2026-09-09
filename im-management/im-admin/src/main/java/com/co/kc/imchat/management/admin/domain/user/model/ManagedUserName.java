package com.co.kc.imchat.management.admin.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：管理边界内的普通用户名。
 */
public record ManagedUserName(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public ManagedUserName {
        AssertUtils.domainPropNotBlank("managed username must not be blank", value);
    }
}
