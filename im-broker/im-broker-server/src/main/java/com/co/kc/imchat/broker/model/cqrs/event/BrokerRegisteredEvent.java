package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

public record BrokerRegisteredEvent(String brokerId, String host, int port,
                                    LocalDateTime occurredOn) implements DomainEvent {

    public BrokerRegisteredEvent(String brokerId, String host, int port) {
        this(brokerId, host, port, LocalDateTime.now());
    }
}
