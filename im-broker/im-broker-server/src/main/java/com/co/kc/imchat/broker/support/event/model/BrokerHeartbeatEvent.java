package com.co.kc.imchat.broker.support.event.model;

import java.time.LocalDateTime;

public record BrokerHeartbeatEvent(String brokerId, LocalDateTime occurredOn) implements BrokerEvent {

    public BrokerHeartbeatEvent(String brokerId) {
        this(brokerId, LocalDateTime.now());
    }
}
