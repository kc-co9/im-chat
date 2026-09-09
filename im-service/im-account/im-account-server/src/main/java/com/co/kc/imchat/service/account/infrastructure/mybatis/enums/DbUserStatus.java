package com.co.kc.imchat.service.account.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户业务状态持久化枚举。
 */
@Getter
@AllArgsConstructor
public enum DbUserStatus {
    NORMAL(1),
    BANNED(2);

    @EnumValue
    private final int code;
}
