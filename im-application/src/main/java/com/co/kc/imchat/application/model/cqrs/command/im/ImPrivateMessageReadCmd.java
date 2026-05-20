package com.co.kc.imchat.application.model.cqrs.command.im;

public record ImPrivateMessageReadCmd(
        /* 聊天ID */
        Long chatId,
        /* 用户ID */
        Long userId,
        /* 消息ID */
        Long messageId
) {
}
