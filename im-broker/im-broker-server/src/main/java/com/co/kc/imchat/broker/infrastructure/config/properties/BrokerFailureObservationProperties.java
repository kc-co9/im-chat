package com.co.kc.imchat.broker.infrastructure.config.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 自动故障观察的采样与接受预算；长连接获取不承诺固定同步返回期限。
 */
@Data
@ConfigurationProperties(prefix = "im.broker.failure-observation")
public class BrokerFailureObservationProperties {
    private Duration probeInterval = Duration.ofSeconds(1);
    private Duration reportInterval = Duration.ofSeconds(1);
    private Duration rpcTimeout = Duration.ofMillis(500);
    private Duration stableWindow = Duration.ofSeconds(30);
    private Duration freshness = Duration.ofSeconds(10);
    private Duration cooldown = Duration.ofSeconds(60);
    private int maxConcurrentRequests = 4;
    private int maxPendingRequests = 64;

    public void validate() {
        positive(probeInterval, "probeInterval");
        positive(reportInterval, "reportInterval");
        positive(rpcTimeout, "rpcTimeout");
        positive(stableWindow, "stableWindow");
        positive(freshness, "freshness");
        positive(cooldown, "cooldown");
        AssertUtils.argTrue("sampling intervals must be shorter than freshness",
                probeInterval.compareTo(freshness) < 0 && reportInterval.compareTo(freshness) < 0);
        AssertUtils.argTrue("rpcTimeout must not exceed freshness", rpcTimeout.compareTo(freshness) <= 0);
        AssertUtils.argTrue("observation concurrency must be positive", maxConcurrentRequests > 0);
        AssertUtils.argTrue("observation queue capacity must be positive", maxPendingRequests > 0);
    }

    private static void positive(Duration value, String name) {
        AssertUtils.argTrue(name + " must be positive", value != null && !value.isZero() && !value.isNegative());
    }
}
