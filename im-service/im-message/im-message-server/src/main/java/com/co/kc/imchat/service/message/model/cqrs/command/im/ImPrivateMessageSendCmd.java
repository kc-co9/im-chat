package com.co.kc.imchat.service.message.model.cqrs.command.im;

import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;

public record ImPrivateMessageSendCmd(
        /* 用户ID */
        Long userId,
        /* 聊天ID */
        Long chatId,
        /* 消息幂等 token */
        String messageToken,
        /* 消息类型 */
        ImMessageType messageType,
        /* 消息内容 */
        String messageContent
) {
}
