package com.co.kc.imchat.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：已加密用户密码。
 */
public record UserPassword(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserPassword {
        AssertUtils.domainPropNotBlank("密码不能为空", value);
    }}
