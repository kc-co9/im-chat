package com.co.kc.imchat.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：用户名。
 */
public record UserName(String value) {
    public UserName {
        AssertUtils.domainPropNotBlank("用户名不能为空", value);
    }}
