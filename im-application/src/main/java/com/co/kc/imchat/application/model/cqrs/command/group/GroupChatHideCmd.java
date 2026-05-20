package com.co.kc.imchat.application.model.cqrs.command.group;

public record GroupChatHideCmd(
        /* 用户ID */
        Long userId,
        /* 聊天ID */
        Long chatId
) {
}
