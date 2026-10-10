package com.co.kc.imchat.broker.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Broker 注册表存储配置。
 */
@Data
@ConfigurationProperties(prefix = "im.broker.registry")
public class RegistryProperties {

    /* Gateway 注册观察的活跃窗口；Lookup responder 与投递前复核使用同一 TTL。 */
    private Duration gatewayTtl = Duration.ofSeconds(60);

}
