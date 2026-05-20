package com.co.kc.imchat.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DbGroupImMessageStatus {
    /**
     * 0-未知
     */
    NONE(0),
    /**
     * 1-已发送
     */
    SENT(1),
    /**
     * 2-已接收
     */
    RECEIVED(2),
    /**
     * 3-已读
     */
    READ(3),
    /**
     * 4-已撤回
     */
    REVOKED(4);

    @EnumValue
    private final int code;
}
