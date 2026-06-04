package com.co.kc.imchat.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：用户名。
 */
public record UserName(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserName {
        AssertUtils.domainPropNotBlank("用户名不能为空", value);
    }}
