package com.co.kc.imchat.service.account.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 用户详情Query
 *
 * @author kc
 */
public record UserDetailQuery(
        /* 用户ID */
        Long userId
) {
    public UserDetailQuery {
        AssertUtils.argNotNull("用户ID不能为空", userId);
        AssertUtils.argTrue("用户ID必须大于0", userId > 0);
    }
}
