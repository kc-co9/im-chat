package com.co.kc.imchat.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：已加密用户密码。
 */
public record UserPassword(String value) {
    public UserPassword {
        AssertUtils.domainPropNotBlank("密码不能为空", value);
    }}
