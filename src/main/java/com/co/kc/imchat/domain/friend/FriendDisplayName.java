package com.co.kc.imchat.domain.friend;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class FriendDisplayName {
    private String value;

    public FriendDisplayName(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("value cannot be null or empty");
        }
        this.value = value;
    }
}
