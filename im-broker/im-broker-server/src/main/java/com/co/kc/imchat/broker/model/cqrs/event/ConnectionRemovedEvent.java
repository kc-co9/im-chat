package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

/** 用户到 Gateway 的连接路由已移除的事件，供 Gossip 传播连接位置删除观察。 */
public record ConnectionRemovedEvent(Long userId, String gatewayId, LocalDateTime occurredOn)
        implements DomainEvent {

    public ConnectionRemovedEvent(Long userId, String gatewayId) {
        this(userId, gatewayId, LocalDateTime.now());
    }
}
