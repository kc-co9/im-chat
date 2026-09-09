package com.co.kc.imchat.management.monitor.infrastructure.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Monitor 查询 Broker 节点的配置。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "im.monitor.broker")
public class MonitorBrokerProperties {
    @NotBlank
    private String serviceName = "im-broker";

    @Min(1)
    private int requestTimeoutMillis = 3_000;
}
