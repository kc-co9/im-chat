package com.co.kc.imchat.broker.support.event.model;

import java.time.LocalDateTime;

public record ConnectionRegisteredEvent(Long userId,
                                        String gatewayId,
                                        LocalDateTime occurredOn) implements BrokerEvent {

    public ConnectionRegisteredEvent(Long userId,
                                     String gatewayId) {
        this(userId, gatewayId, LocalDateTime.now());
    }
}
