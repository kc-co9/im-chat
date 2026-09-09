package com.co.kc.imchat.service.account.admin.facade.params;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/** 封禁普通用户参数。 */
public record UserBanParams(Long userId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserBanParams {
        AssertUtils.argTrue("userId must be positive", userId != null && userId > 0);
    }
}
