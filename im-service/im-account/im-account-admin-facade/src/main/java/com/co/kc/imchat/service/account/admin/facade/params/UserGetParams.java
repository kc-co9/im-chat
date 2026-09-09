package com.co.kc.imchat.service.account.admin.facade.params;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/** 普通用户详情查询参数。 */
public record UserGetParams(Long userId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserGetParams {
        AssertUtils.argTrue("userId must be positive", userId != null && userId > 0);
    }
}
