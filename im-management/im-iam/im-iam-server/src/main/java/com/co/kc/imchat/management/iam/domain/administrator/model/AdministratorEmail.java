package com.co.kc.imchat.management.iam.domain.administrator.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.regex.Pattern;

/** IAM 管理员邮箱。 */
public record AdministratorEmail(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:\\.[a-zA-Z]{2,})?$");

    public AdministratorEmail {
        AssertUtils.domainPropNotBlank("administrator email must not be blank", value);
        AssertUtils.domainPropTrue(
                "administrator email format is invalid",
                isValid(value));
    }

    /** 判断原始值是否符合管理员邮箱格式。 */
    public static boolean isValid(String value) {
        return value != null && EMAIL_PATTERN.matcher(value).matches();
    }
}
