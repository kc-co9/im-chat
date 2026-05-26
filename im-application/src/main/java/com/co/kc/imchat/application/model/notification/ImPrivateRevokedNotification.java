package com.co.kc.imchat.application.model.notification;

public record ImPrivateRevokedNotification(
        /* 接收人用户ID */
        Long receiverId,
        /* 接收人聊天ID */
        Long receiverChatId,
        /* 消息ID */
        Long messageId
) {
}
