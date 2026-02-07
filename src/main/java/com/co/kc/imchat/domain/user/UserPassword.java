package com.co.kc.imchat.domain.user;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class UserPassword {
    private final String value;

    public UserPassword(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("password is null or empty");
        }
        this.value = value;
    }
}
