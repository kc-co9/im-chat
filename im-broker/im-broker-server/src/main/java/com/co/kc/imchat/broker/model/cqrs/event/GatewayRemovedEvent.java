package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

public record GatewayRemovedEvent(String gatewayId, LocalDateTime occurredOn)
        implements DomainEvent {

    public GatewayRemovedEvent(String gatewayId) {
        this(gatewayId, LocalDateTime.now());
    }
}
