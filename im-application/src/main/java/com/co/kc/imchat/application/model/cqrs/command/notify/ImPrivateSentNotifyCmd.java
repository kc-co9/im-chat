package com.co.kc.imchat.application.model.cqrs.command.notify;

import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;

import java.time.LocalDateTime;

public record ImPrivateSentNotifyCmd(
        /* 消息ID */
        Long messageId,
        /* 接收人聊天ID */
        Long chatId,
        /* 发送人用户ID */
        Long senderId,
        /* 接收人用户ID */
        Long receiverId,
        /* 消息类型 */
        ImMessageTypeEnum messageType,
        /* 消息内容 */
        String messageContent,
        /* 发送时间 */
        LocalDateTime sendTime
) {
}
