package com.co.kc.imchat.application.model.cqrs.command.group;

import com.co.kc.imchat.domain.message.model.ImMessageType;

public record GroupMessageSendCmd(
        /* 聊天ID */
        Long chatId,
        /* 发送人用户ID */
        Long senderId,
        /* 消息幂等 token */
        String messageToken,
        /* 消息类型 */
        ImMessageType messageType,
        /* 消息内容 */
        String messageContent
) {
}
