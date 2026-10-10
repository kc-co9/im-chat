package com.co.kc.imchat.broker.infrastructure.monitoring;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.adapter.gossip.BrokerStateStore;
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
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gateway-1")), new RouteRevision(0L), Instant.MAX));
        stateStore.putConnectionState(1L, "gateway-1");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new BrokerMetrics(brokers, connections, stateStore).bindTo(registry);

        assertThat(registry.get("im.broker.instances").gauge().value()).isEqualTo(1);
        assertThat(registry.get("im.broker.connections").gauge().value()).isEqualTo(1);
        assertThat(registry.get("im.broker.gossip.entries").gauge().value()).isEqualTo(1);
    }
}
