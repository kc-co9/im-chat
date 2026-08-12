package com.co.kc.imchat.service.message.model.cqrs.command.chat;

public record GroupAliasChangeCmd(
        /* 用户ID */
        Long userId,
        /* 聊天ID */
        Long chatId,
        /* 群聊备注 */
        String groupAlias
) {
}
