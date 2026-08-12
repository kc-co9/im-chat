package com.co.kc.imchat.service.message.application.notification.task;

public enum ReceiptType {

    PRIVATE_MESSAGE_SEND,
    PRIVATE_MESSAGE_REVOKE,

    GROUP_MESSAGE_SEND,
    GROUP_MESSAGE_REVOKE,
    ;

    public String receiptId(Long receiverId, Long chatId, Long messageId) {
        return String.format("%s:%s:%s:%s", name(), receiverId, chatId, messageId);
    }
}
