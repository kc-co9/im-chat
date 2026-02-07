package com.co.kc.imchat.domain.sticker;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ImStickerId {

    private final String value;

    public ImStickerId(String value) {
        if (value != null && value.trim().isEmpty()) {
            throw new IllegalArgumentException("StickerId cannot be empty");
        }
        this.value = value;
    }

}
