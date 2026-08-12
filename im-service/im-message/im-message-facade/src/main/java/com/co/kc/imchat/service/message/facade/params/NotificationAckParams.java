package com.co.kc.imchat.service.message.facade.params;

public record NotificationAckParams(Long userId, Long chatId, Long messageId, String receiptType) {
}
