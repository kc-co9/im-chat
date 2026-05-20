package com.co.kc.imchat.application.model.cqrs.query.user;

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
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId is less than 0");
        }
    }
}
