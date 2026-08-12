package com.co.kc.imchat.service.message.facade.params;

public record GroupMessageSendParams(Long userId, Long chatId, String messageToken, String messageType, String messageContent) {
}
