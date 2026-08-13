package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;

public record PrivateMessageRevokeParams(Long userId, Long chatId, Long messageId) implements Serializable {
}
