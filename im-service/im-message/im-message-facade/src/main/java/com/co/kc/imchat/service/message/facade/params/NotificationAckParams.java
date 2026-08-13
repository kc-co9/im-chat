package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;

public record NotificationAckParams(Long userId, Long chatId, Long messageId, String receiptType) implements Serializable {
}
