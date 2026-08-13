package com.co.kc.imchat.gateway.ws.support.monitoring;

import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * WebSocket 网关本地连接指标。
 */
@Component
public class GatewayMetrics implements MeterBinder {
    private final ConnectionRegistry connectionRegistry;

    public GatewayMetrics(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("im.gateway.connections", connectionRegistry,
                        value -> value.activeConnectionIds().size())
                .description("Active WebSocket connections on this gateway")
                .register(registry);
        Gauge.builder("im.gateway.users", connectionRegistry,
                        value -> value.activeUserIds().size())
                .description("Users with active WebSocket connections on this gateway")
                .register(registry);
    }
}
