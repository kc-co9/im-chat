package com.co.kc.imchat.domain.chat.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ImChatId {
    private final Long value;

    public ImChatId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        this.value = value;
    }
}
