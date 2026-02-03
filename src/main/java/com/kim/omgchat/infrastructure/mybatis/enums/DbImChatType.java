package com.kim.omgchat.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DbImChatType {
    // 0-未知
    NONE(0),
    // 1-单聊
    PRIVATE(1),
    // 2-群聊
    GROUP(2);

    @EnumValue
    private final int code;
}
