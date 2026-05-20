package com.co.kc.imchat.application.model.cqrs.command.chat;

public record PrivateChatHideCmd(
        /* 用户ID */
        Long userId,
        /* 聊天ID */
        Long chatId
) {
}
