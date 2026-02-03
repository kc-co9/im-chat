package com.kim.omgchat.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DbImMessageStatus {
    /**
     * 0-未知
     */
    NONE(0),
    /**
     * 1-已发送
     */
    SENT(1),

    /**
     * 2-已读
     */
    READ(2),

    /**
     * 3-已撤回
     */
    REVOKED(3);

    @EnumValue
    private final int code;
}
