package com.co.kc.imchat.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RedisKey {
    // 聊天会话缓存
    IM_CHAT_SESSION("im:chat:session:"),
    ;

    private final String value;
}
