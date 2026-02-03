package com.kim.omgchat.domain.user;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class UserName {
    private final String value;

    public UserName(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("name is null or empty");
        }
        this.value = value;
    }
}
