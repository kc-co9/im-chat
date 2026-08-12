package com.co.kc.imchat.service.message.model.cqrs.command.group;

public record GroupChatHideCmd(
        /* 用户ID */
        Long userId,
        /* 聊天ID */
        Long chatId
) {
}
