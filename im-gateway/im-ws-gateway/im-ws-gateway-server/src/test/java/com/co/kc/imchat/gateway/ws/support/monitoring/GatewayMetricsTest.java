package com.co.kc.imchat.gateway.ws.support.monitoring;

import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("realtime-behavior")
class GatewayMetricsTest {

    @Test
    void exposesActiveConnectionAndUserCounts() {
        ConnectionRegistry connections = new ConnectionRegistry();
        connections.register(1L, "session-1", "connection-1", new EmbeddedChannel());
        connections.register(1L, "session-1", "connection-2", new EmbeddedChannel());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new GatewayMetrics(connections).bindTo(registry);

        assertThat(registry.get("im.gateway.connections").gauge().value()).isEqualTo(2);
        assertThat(registry.get("im.gateway.users").gauge().value()).isEqualTo(1);
    }
}
