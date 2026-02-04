package com.kim.omgchat.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LogFormat {
    JSON("json"),
    PRETTY("pretty");

    private final String format;

    public static LogFormat getByFormat(String format) {
        for (LogFormat value : values()) {
            if (value.format.equals(format)) {
                return value;
            }
        }
        return null;
    }
}
