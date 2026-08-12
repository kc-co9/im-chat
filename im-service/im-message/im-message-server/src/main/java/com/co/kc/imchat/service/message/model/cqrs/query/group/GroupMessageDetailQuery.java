package com.co.kc.imchat.service.message.model.cqrs.query.group;

public record GroupMessageDetailQuery(
        /* 聊天ID */
        Long chatId,
        /* 用户ID */
        Long userId,
        /* 消息 token */
        String messageToken
) {
}
