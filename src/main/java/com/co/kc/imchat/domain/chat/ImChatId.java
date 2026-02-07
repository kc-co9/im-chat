package com.co.kc.imchat.domain.chat;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ImChatId {
    private final Long value;

    public ImChatId(Long value) {
        this.value = value;
    }
}
