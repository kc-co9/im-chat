package com.co.kc.imchat.domain.sticker.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class ImStickerId {

    private final String value;

    public ImStickerId(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("StickerId cannot be empty");
        }
        this.value = value;
    }

}
