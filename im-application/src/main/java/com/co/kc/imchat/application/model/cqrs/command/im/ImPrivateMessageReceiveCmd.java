package com.co.kc.imchat.application.model.cqrs.command.im;

public record ImPrivateMessageReceiveCmd(
        /* 用户ID */
        Long userId,
        /* 聊天ID */
        Long chatId,
        /* 消息ID */
        Long messageId
) {
}
