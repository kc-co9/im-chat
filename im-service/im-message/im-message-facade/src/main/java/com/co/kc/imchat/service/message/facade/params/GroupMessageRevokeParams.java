package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;

public record GroupMessageRevokeParams(Long userId, Long chatId, Long messageId) implements Serializable {
}
