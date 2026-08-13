package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;

public record GroupMessageReadParams(Long userId, Long chatId, Long messageId) implements Serializable {
}
