package com.co.kc.imchat.domain.chat;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ImGroupId {
    private final Long value;

    public ImGroupId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        this.value = value;
    }
}
