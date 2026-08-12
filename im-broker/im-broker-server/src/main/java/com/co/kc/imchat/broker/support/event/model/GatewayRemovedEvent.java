package com.co.kc.imchat.broker.support.event.model;

import java.time.LocalDateTime;

public record GatewayRemovedEvent(String gatewayId, LocalDateTime occurredOn) implements BrokerEvent {

    public GatewayRemovedEvent(String gatewayId) {
        this(gatewayId, LocalDateTime.now());
    }
}
