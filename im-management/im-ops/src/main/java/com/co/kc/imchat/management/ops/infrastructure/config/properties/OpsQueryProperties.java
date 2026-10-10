package com.co.kc.imchat.management.ops.infrastructure.config.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ops 多节点查询执行器配置。
 */
@ConfigurationProperties("im.ops.query")
public record OpsQueryProperties(
        Integer threads,
        Integer queueCapacity
) {
    public OpsQueryProperties {
        AssertUtils.argTrue("ops query threads must be positive",
                threads != null && threads > 0);
        AssertUtils.argTrue("ops query queue capacity must be positive",
                queueCapacity != null && queueCapacity > 0);
    }
}
