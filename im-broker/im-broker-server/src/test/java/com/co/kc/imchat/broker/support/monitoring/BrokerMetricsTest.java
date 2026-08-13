package com.co.kc.imchat.broker.support.monitoring;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.domain.store.BrokerStateStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("realtime-behavior")
class BrokerMetricsTest {

    @Test
    void exposesBrokerConnectionAndGossipState() {
        InMemoryBrokerRegistry brokers = new InMemoryBrokerRegistry();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        BrokerStateStore stateStore = new BrokerStateStore(brokers, new InMemoryGatewayRegistry(), connections,
                new BrokerProperties(), new ClusterProperties());
        brokers.register("broker-1", "127.0.0.1", 12200);
        connections.register(1L, "gateway-1");
        stateStore.putConnectionState(1L, "gateway-1");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new BrokerMetrics(brokers, connections, stateStore).bindTo(registry);

        assertThat(registry.get("im.broker.instances").gauge().value()).isEqualTo(1);
        assertThat(registry.get("im.broker.connections").gauge().value()).isEqualTo(1);
        assertThat(registry.get("im.broker.gossip.entries").gauge().value()).isEqualTo(1);
    }
}
