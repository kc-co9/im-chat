package com.co.kc.imchat.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DbImGroupStatus {
    /**
     * 0-未知
     */
    NONE(0),
    /**
     * 1-正常
     */
    NORMAL(1),
    /**
     * 2-已解散
     */
    DISMISSED(2);

    @EnumValue
    private final int code;
}
