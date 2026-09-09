package com.co.kc.imchat.service.account.admin.facade.params;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/** 管理员重置普通用户密码参数。 */
public record UserPasswordResetParams(Long userId, String newPassword) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserPasswordResetParams {
        AssertUtils.argTrue("userId must be positive", userId != null && userId > 0);
        AssertUtils.argNotBlank("newPassword must not be blank", newPassword);
    }
}
