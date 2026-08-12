package com.co.kc.imchat.service.social.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DbFriendStatus {
    // 0-未知
    NONE(0),
    // 1-正常
    NORMAL(1),
    // 2-拉黑
    BLOCKED(2),
    // 3-删除
    DELETED(3);

    @EnumValue
    private final int code;
}
