package com.co.kc.imchat.application.model.notification;

public record ImGroupRevokedNotification(
        /* 接收人聊天ID */
        Long chatId,
        /* 消息ID */
        Long messageId,
        /* 接收人用户ID */
        Long receiverId
) {
}
