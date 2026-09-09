package com.co.kc.imchat.management.monitor.infrastructure.config.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Monitor 多节点查询执行器配置。
 */
@ConfigurationProperties("im.monitor.query")
public record MonitorQueryProperties(
        Integer threads,
        Integer queueCapacity
) {
    public MonitorQueryProperties {
        AssertUtils.argTrue("monitor query threads must be positive",
                threads != null && threads > 0);
        AssertUtils.argTrue("monitor query queue capacity must be positive",
                queueCapacity != null && queueCapacity > 0);
    }
}
