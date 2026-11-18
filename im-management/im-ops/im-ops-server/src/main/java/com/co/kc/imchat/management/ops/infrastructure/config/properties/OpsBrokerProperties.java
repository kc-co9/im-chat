package com.co.kc.imchat.management.ops.infrastructure.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Ops 查询 Broker 节点的配置。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "im.ops.broker")
public class OpsBrokerProperties {
    @NotBlank
    private String serviceName = "im-broker";

    @Min(1)
    private int requestTimeoutMillis = 3_000;

    @NotBlank
    private String managementAudience = "im-broker-management";

    @NotBlank
    private String managementToken = "local-ops-management-token";
}
