package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

/** Gateway 注册观察已移除的事件，供 Gossip 传播网关删除状态。 */
public record GatewayRemovedEvent(String gatewayId, LocalDateTime occurredOn)
        implements DomainEvent {

    public GatewayRemovedEvent(String gatewayId) {
        this(gatewayId, LocalDateTime.now());
    }
}
