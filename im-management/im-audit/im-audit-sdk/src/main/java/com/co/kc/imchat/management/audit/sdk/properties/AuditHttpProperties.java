package com.co.kc.imchat.management.audit.sdk.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/** 异步 HTTP 审计投递配置。 */
public record AuditHttpProperties(
        /* Audit 内部接收地址。 */
        URI endpoint,
        /* 单次 HTTP 请求超时。 */
        @DefaultValue("3s") Duration timeout,
        /* 异步投递线程数。 */
        @DefaultValue("2") Integer workerThreads,
        /* 异步等待队列容量。 */
        @DefaultValue("1000") Integer queueCapacity,
        /* 包含首次请求的最大尝试次数。 */
        @DefaultValue("3") Integer maxAttempts,
        /* 可重试失败的等待时间。 */
        @DefaultValue("200ms") Duration retryDelay
) {
    public AuditHttpProperties {
        AssertUtils.allArgNotNull(
                "audit HTTP numeric and duration properties must not be null",
                timeout,
                workerThreads,
                queueCapacity,
                maxAttempts,
                retryDelay);
        AssertUtils.argTrue("audit HTTP timeout must be positive", !timeout.isNegative()
                && !timeout.isZero());
        AssertUtils.argTrue("audit HTTP worker threads must be positive", workerThreads > 0);
        AssertUtils.argTrue("audit HTTP queue capacity must be positive", queueCapacity > 0);
        AssertUtils.argTrue("audit HTTP max attempts must be positive", maxAttempts > 0);
        AssertUtils.argTrue("audit HTTP retry delay must not be negative", !retryDelay.isNegative());
    }

    /** 校验 HTTP 被选中时必须存在的远程配置。 */
    public void validateForUse() {
        AssertUtils.argNotNull("audit HTTP endpoint must not be null", endpoint);
    }
}
