package com.co.kc.imchat.service.message.model.cqrs.query;

public record ImPrivateMessageDetailQuery(
        /* 聊天ID */
        Long chatId,
        /* 用户ID */
        Long userId,
        /* 消息 token */
        String messageToken
) {
}
