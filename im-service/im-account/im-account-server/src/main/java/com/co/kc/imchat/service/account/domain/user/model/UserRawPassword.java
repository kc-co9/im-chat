package com.co.kc.imchat.service.account.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.regex.Pattern;

/**
 * 值对象：用户明文密码。
 */
public record UserRawPassword(String value) {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&()_+\\-=\\[\\]{}|;:,.<>/?~`]+$");

    public UserRawPassword {
        AssertUtils.domainPropNotBlank("密码不能为空", value);
        AssertUtils.domainPropTrue("密码包含非法字符", PASSWORD_PATTERN.matcher(value).matches());
    }}
