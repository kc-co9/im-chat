package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

public record GatewayRegisteredEvent(String gatewayId, String host, int port,
                                     String workloadIdentity, LocalDateTime occurredOn)
        implements DomainEvent {

    public GatewayRegisteredEvent(String gatewayId, String host, int port,
                                  String workloadIdentity) {
        this(gatewayId, host, port, workloadIdentity, LocalDateTime.now());
    }
}
