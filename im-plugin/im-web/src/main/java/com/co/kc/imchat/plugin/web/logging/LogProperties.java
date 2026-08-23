package com.co.kc.imchat.plugin.web.logging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author kc
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "log")
public class LogProperties {
    private String path;
    private String format;

    public LogFormat getLogFormat() {
        return LogFormat.getByFormat(format);
    }
}
