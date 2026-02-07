package com.co.kc.imchat.domain.user;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 用户ID
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class UserId {
    private final Long value;
}
