package com.co.kc.imchat.broker.support.event.model;

import java.time.LocalDateTime;

public record GatewayRegisteredEvent(String gatewayId, String host, int port, LocalDateTime occurredOn)
        implements BrokerEvent {

    public GatewayRegisteredEvent(String gatewayId, String host, int port) {
        this(gatewayId, host, port, LocalDateTime.now());
    }
}
