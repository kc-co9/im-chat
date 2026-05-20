package com.co.kc.imchat.application.model.cqrs.query.user;

import lombok.Data;

/**
 * 用户详情Query
 *
 * @author kc
 */
@Data
public class UserDetailQuery {
    private Long userId;

    public UserDetailQuery(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId is less than 0");
        }
        this.userId = userId;
    }
}
