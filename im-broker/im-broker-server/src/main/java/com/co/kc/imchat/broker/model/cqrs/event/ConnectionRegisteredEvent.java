package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

/** 用户到 Gateway 的连接路由已注册的事件，供 Gossip 记录连接位置观察。 */
public record ConnectionRegisteredEvent(Long userId,
                                        String gatewayId,
                                        LocalDateTime occurredOn) implements DomainEvent {

    public ConnectionRegisteredEvent(Long userId,
                                     String gatewayId) {
        this(userId, gatewayId, LocalDateTime.now());
    }
}
