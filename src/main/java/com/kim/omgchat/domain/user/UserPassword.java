package com.kim.omgchat.domain.user;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class UserPassword {
    private final String value;

    public UserPassword(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("password is null or empty");
        }
        this.value = value;
    }
}
