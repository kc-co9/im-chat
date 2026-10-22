package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

/** Broker 注册观察已移除的事件，供 Gossip 传播删除状态，不表示权威拓扑变更。 */
public record BrokerRemovedEvent(String brokerId, LocalDateTime occurredOn)
        implements DomainEvent {

    public BrokerRemovedEvent(String brokerId) {
        this(brokerId, LocalDateTime.now());
    }
}
