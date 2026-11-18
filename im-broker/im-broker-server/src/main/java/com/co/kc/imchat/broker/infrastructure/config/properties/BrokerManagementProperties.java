package com.co.kc.imchat.broker.infrastructure.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Broker 管理接口、服务身份与诊断历史配置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "im.broker.management")
public class BrokerManagementProperties {
    /* 管理 HTTP 服务监听地址；生产可与对外发布地址不同。 */
    @NotBlank
    private String bindHost = "127.0.0.1";

    /* im-ops 访问管理接口时使用的发布主机。 */
    @NotBlank
    private String host = "127.0.0.1";

    /* 管理 HTTP 服务监听端口，不得与 Broker Bolt 端口冲突。 */
    @Min(1)
    @Max(65_535)
    private int port = 19_020;

    /* 诊断历史最多保留的记录数，限制本机内存占用。 */
    @Min(1)
    @Max(1_000)
    private int historyCapacity = 100;

    /* 管理写请求要求的受众，必须与调用方凭证配置一致。 */
    @NotBlank
    private String writeAudience = "im-broker-management";

    /* 本地开发默认凭证；生产必须覆盖并限制管理端口访问。 */
    @NotBlank
    private String writeToken = "local-ops-management-token";

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
