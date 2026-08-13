package com.co.kc.imchat.service.account.facade.params;

import java.io.Serializable;

public record UserChattingCheckParams(Long userId, Long chatId) implements Serializable {
}
