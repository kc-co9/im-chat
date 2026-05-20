package com.co.kc.imchat.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DbImChatStatus {
    /**
     * 0-未知
     */
    UNKNOWN(0),
    /**
     * 1-正常
     */
    NORMAL(1),
    /**
     * 2-隐藏
     */
    HIDDEN(2);

    @EnumValue
    private final int code;
}
