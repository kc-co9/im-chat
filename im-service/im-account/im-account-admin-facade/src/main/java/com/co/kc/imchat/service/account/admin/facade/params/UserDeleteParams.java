package com.co.kc.imchat.service.account.admin.facade.params;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/** 逻辑删除普通用户参数。 */
public record UserDeleteParams(Long userId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserDeleteParams {
        AssertUtils.argTrue("userId must be positive", userId != null && userId > 0);
    }
}
