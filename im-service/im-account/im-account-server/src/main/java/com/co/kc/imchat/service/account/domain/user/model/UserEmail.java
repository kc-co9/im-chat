package com.co.kc.imchat.service.account.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * 值对象：用户邮箱。
 */
public record UserEmail(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:\\.[a-zA-Z]{2,})?$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    public UserEmail {
        AssertUtils.domainPropNotBlank("邮箱不能为空", value);
        AssertUtils.domainPropTrue("邮箱格式不合法", EMAIL_PATTERN.matcher(value).matches());
    }}
