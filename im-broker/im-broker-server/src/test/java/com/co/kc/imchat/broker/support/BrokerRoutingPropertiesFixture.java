package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;

import java.time.Duration;

/** 为本机路由容量与超时测试构造源配置，不引入生产配置包装。 */
public final class BrokerRoutingPropertiesFixture {
    private BrokerRoutingPropertiesFixture() {
    }

    public static BrokerRoutingProperties routingProperties(
            int maxPendingBatches, long maxPendingBytes, Duration batchTimeout,
            int cleanupResultCapacity) {
        BrokerRoutingProperties properties = new BrokerRoutingProperties();
        properties.setMaxPendingSnapshotBatches(maxPendingBatches);
        properties.setMaxPendingSnapshotBytes(maxPendingBytes);
        properties.setSnapshotBatchTimeout(batchTimeout);
        properties.setDrainCleanupResultCapacity(cleanupResultCapacity);
        return properties;
    }
}
