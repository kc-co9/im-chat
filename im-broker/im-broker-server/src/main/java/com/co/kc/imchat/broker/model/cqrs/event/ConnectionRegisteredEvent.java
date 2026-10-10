package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

public record ConnectionRegisteredEvent(Long userId,
                                        String gatewayId,
                                        LocalDateTime occurredOn) implements DomainEvent {

    public ConnectionRegisteredEvent(Long userId,
                                     String gatewayId) {
        this(userId, gatewayId, LocalDateTime.now());
    }
}
