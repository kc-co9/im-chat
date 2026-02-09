package com.co.kc.imchat.domain.message;

import lombok.Getter;

@Getter
public class ImMessageId {
    private final Long value;

    public ImMessageId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        this.value = value;
    }
}
