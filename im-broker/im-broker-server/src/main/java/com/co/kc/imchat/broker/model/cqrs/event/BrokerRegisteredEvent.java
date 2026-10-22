package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

/** Broker 已注册的运行观察事件，携带实例身份与端点供 Gossip 同步。 */
public record BrokerRegisteredEvent(String brokerId, String host, int port,
                                    LocalDateTime occurredOn) implements DomainEvent {

    public BrokerRegisteredEvent(String brokerId, String host, int port) {
        this(brokerId, host, port, LocalDateTime.now());
    }
}
