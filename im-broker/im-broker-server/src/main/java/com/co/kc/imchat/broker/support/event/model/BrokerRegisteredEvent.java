package com.co.kc.imchat.broker.support.event.model;

import java.time.LocalDateTime;

public record BrokerRegisteredEvent(String brokerId, String host, int port, LocalDateTime occurredOn)
        implements BrokerEvent {

    public BrokerRegisteredEvent(String brokerId, String host, int port) {
        this(brokerId, host, port, LocalDateTime.now());
    }
}
