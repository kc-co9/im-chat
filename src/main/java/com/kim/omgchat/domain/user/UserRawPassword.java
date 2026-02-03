package com.kim.omgchat.domain.user;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

@Getter
public class UserRawPassword {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&()_+\\-=\\[\\]{}|;:,.<>/?~`]+$");

    private final String value;

    public UserRawPassword(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("password is null or empty");
        }
        if (!PASSWORD_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("password contains illegal characters");
        }
        this.value = value;
    }
}
