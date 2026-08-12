package com.co.kc.imchat.service.message.model.cqrs.command.group;

public record GroupMessageReceiveCmd(
        /* 用户ID */
        Long userId,
        /* 聊天ID */
        Long chatId,
        /* 消息ID */
        Long messageId
) {
}
