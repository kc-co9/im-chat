package com.co.kc.imchat.plugin.web.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author kc
 */
@ConfigurationProperties(prefix = "log")
public class LogProperties {
    private String path;
    private String format;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public LogFormat getLogFormat() {
        return LogFormat.getByFormat(format);
    }
}
