package com.co.kc.imchat.service.account.facade.params;

import java.io.Serializable;

public record ChatEnterParams(Long userId, Long chatId) implements Serializable {
}
