package com.co.kc.imchat.management.admin.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * 值对象：管理边界内的普通用户邮箱。
 */
public record ManagedUserEmail(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String EMAIL_REGEX =
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:\\.[a-zA-Z]{2,})?$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    public ManagedUserEmail {
        AssertUtils.domainPropNotBlank("managed user email must not be blank", value);
        AssertUtils.domainPropTrue(
                "managed user email format is invalid",
                EMAIL_PATTERN.matcher(value).matches());
    }
}
