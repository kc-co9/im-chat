package com.co.kc.imchat.broker.support.event.model;

import java.time.LocalDateTime;

public record BrokerRemovedEvent(String brokerId, LocalDateTime occurredOn) implements BrokerEvent {

    public BrokerRemovedEvent(String brokerId) {
        this(brokerId, LocalDateTime.now());
    }
}
