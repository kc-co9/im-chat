package com.co.kc.imchat.service.message.facade.params;

public record PrivateMessageReadParams(Long userId, Long chatId, Long messageId) {
}
