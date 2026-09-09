package com.co.kc.imchat.broker.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Broker 只读管理接口与诊断历史配置。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "im.broker.management")
public class BrokerManagementProperties {
    @NotBlank
    private String host = "127.0.0.1";

    @Min(1)
    @Max(65_535)
    private int port = 12_201;

    @Min(1)
    @Max(1_000)
    private int historyCapacity = 100;

    /**
     * 校验管理 HTTP 端口不与 Bolt 监听端口冲突。
     *
     * @param boltPort Bolt 监听端口
     */
    public void validateBoltPort(int boltPort) {
        if (port == boltPort) {
            throw new IllegalStateException("im.broker.management.port must differ from im.bolt.server.port");
        }
    }
}
