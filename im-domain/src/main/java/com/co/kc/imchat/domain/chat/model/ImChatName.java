package com.co.kc.imchat.domain.chat.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class ImChatName {
    private final String value;

    public ImChatName(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("ChatName cannot be blank");
        }
        this.value = value;
    }
}
