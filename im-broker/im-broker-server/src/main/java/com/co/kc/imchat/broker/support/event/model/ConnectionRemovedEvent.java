package com.co.kc.imchat.broker.support.event.model;

import java.time.LocalDateTime;

public record ConnectionRemovedEvent(Long userId, String gatewayId, LocalDateTime occurredOn)
        implements BrokerEvent {

    public ConnectionRemovedEvent(Long userId, String gatewayId) {
        this(userId, gatewayId, LocalDateTime.now());
    }
}
