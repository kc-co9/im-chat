package com.co.kc.imchat.broker.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Broker 注册表存储配置。
 */
@Data
@ConfigurationProperties(prefix = "im.broker.registry")
public class RegistryProperties {

    /**
     * Broker 实例无心跳后的有效保留时间。
     */
    private Duration brokerTtl = Duration.ofSeconds(60);

    /**
     * Gateway 实例无心跳后的有效保留时间。
     */
    private Duration gatewayTtl = Duration.ofSeconds(60);

    /**
     * 用户与 Gateway 连接映射的有效保留时间。
     */
    private Duration connectionTtl = Duration.ofSeconds(60);
}
