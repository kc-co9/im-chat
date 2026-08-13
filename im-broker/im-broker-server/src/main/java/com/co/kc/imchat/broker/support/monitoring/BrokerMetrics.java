package com.co.kc.imchat.broker.support.monitoring;

import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.store.BrokerStateStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Broker 核心运行状态指标。
 */
@Component
public class BrokerMetrics implements MeterBinder {
    private final BrokerRegistry brokerRegistry;
    private final ConnectionRegistry connectionRegistry;
    private final BrokerStateStore brokerStateStore;

    public BrokerMetrics(BrokerRegistry brokerRegistry,
                         ConnectionRegistry connectionRegistry,
                         BrokerStateStore brokerStateStore) {
        this.brokerRegistry = brokerRegistry;
        this.connectionRegistry = connectionRegistry;
        this.brokerStateStore = brokerStateStore;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("im.broker.instances", brokerRegistry, value -> value.list().size())
                .description("Broker instances currently known by this node")
                .register(registry);
        Gauge.builder("im.broker.connections", connectionRegistry, value -> value.list().size())
                .description("User to gateway mappings owned by this Broker")
                .register(registry);
        Gauge.builder("im.broker.gossip.entries", brokerStateStore, value -> value.digest().size())
                .description("Gossip state entries currently retained by this Broker")
                .register(registry);
    }
}
