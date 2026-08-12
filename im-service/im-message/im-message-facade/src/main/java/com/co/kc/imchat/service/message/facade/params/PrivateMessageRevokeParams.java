package com.co.kc.imchat.service.message.facade.params;

public record PrivateMessageRevokeParams(Long userId, Long chatId, Long messageId) {
}
