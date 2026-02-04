package com.kim.omgchat.infrastructure.config.properties;

import com.kim.omgchat.model.enums.LogFormat;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author kc
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "log")
public class LogProperties {
    private String path;
    private String format;

    public LogFormat getLogFormat() {
        return LogFormat.getByFormat(format);
    }
}
