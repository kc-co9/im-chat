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
     * 4-已撤回
     */
    REVOKED(2);

    @EnumValue
    private final int code;
}
