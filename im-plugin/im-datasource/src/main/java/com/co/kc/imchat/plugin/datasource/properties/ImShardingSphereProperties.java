package com.co.kc.imchat.plugin.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "im.datasource.sharding")
public class ImShardingSphereProperties {
    private boolean enabled;

    private String configLocation;
}
