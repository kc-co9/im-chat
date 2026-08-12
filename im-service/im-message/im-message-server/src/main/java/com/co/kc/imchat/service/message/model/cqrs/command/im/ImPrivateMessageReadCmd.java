package com.co.kc.imchat.service.message.model.cqrs.command.im;

public record ImPrivateMessageReadCmd(
        /* 聊天ID */
        Long chatId,
        /* 用户ID */
        Long userId,
        /* 消息ID */
        Long messageId
) {
}
