package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

public record BrokerHeartbeatEvent(String brokerId, LocalDateTime occurredOn)
        implements DomainEvent {

    public BrokerHeartbeatEvent(String brokerId) {
        this(brokerId, LocalDateTime.now());
    }
}
