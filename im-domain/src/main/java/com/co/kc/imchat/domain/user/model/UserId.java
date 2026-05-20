package com.co.kc.imchat.domain.user.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 用户ID
 */
@Getter
@EqualsAndHashCode
public class UserId {
    private final Long value;

    public UserId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        this.value = value;
    }
}
