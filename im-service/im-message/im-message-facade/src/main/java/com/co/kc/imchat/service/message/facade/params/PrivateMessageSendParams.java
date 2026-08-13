package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;

public record PrivateMessageSendParams(Long userId, Long chatId, String messageToken, String messageType, String messageContent) implements Serializable {
}
