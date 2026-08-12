package com.co.kc.imchat.broker.support.event.model;

import java.time.LocalDateTime;

public record GatewayHeartbeatEvent(String gatewayId, LocalDateTime occurredOn) implements BrokerEvent {

    public GatewayHeartbeatEvent(String gatewayId) {
        this(gatewayId, LocalDateTime.now());
    }
}
