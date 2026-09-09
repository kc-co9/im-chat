package com.co.kc.imchat.service.account.admin.facade.params;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/** 修改普通用户资料参数。 */
public record UserUpdateParams(Long userId, String username, String email) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserUpdateParams {
        AssertUtils.argTrue("userId must be positive", userId != null && userId > 0);
        AssertUtils.argNotBlank("username must not be blank", username);
        AssertUtils.argNotBlank("email must not be blank", email);
        username = username.trim();
        email = email.trim();
    }
}
