package com.co.kc.imchat.plugin.web.logging;

public enum LogFormat {
    JSON("json"),
    PRETTY("pretty");

    private final String format;

    LogFormat(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    public static LogFormat getByFormat(String format) {
        for (LogFormat value : values()) {
            if (value.format.equals(format)) {
                return value;
            }
        }
        return PRETTY;
    }
}
