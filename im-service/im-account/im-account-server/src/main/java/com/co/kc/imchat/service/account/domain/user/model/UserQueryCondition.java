package com.co.kc.imchat.service.account.domain.user.model;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Optional;

/**
 * 用户管理查询筛选条件。
 */
public record UserQueryCondition(
        Optional<UserId> userId,
        Optional<UserName> username,
        Optional<UserEmail> email,
        Optional<UserStatus> status
) {
    public UserQueryCondition {
        AssertUtils.allArgNotNull(
                "user query condition options must not be null",
                userId, username, email, status);
    }
}
