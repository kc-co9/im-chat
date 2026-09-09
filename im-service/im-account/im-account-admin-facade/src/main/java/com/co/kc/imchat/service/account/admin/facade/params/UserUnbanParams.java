package com.co.kc.imchat.service.account.admin.facade.params;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/** 解除普通用户封禁参数。 */
public record UserUnbanParams(Long userId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserUnbanParams {
        AssertUtils.argTrue("userId must be positive", userId != null && userId > 0);
    }
}
