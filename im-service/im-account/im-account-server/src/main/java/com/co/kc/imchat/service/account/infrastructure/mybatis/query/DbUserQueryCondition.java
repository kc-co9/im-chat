package com.co.kc.imchat.service.account.infrastructure.mybatis.query;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.service.account.infrastructure.mybatis.enums.DbUserStatus;
import java.util.Optional;

/**
 * 管理端用户查询的持久化条件。
 */
public record DbUserQueryCondition(
        Optional<Long> userId,
        Optional<String> username,
        Optional<String> email,
        Optional<DbUserStatus> status
) {
    public DbUserQueryCondition {
        AssertUtils.allArgNotNull(
                "user query condition options must not be null",
                userId, username, email, status);
        username = username.filter(value -> !value.isBlank());
        email = email.filter(value -> !value.isBlank());
    }
}
