package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

/** Gateway 心跳已更新的运行观察事件，供监听器刷新 Gossip 投影。 */
public record GatewayHeartbeatEvent(String gatewayId, LocalDateTime occurredOn)
        implements DomainEvent {

    public GatewayHeartbeatEvent(String gatewayId) {
        this(gatewayId, LocalDateTime.now());
    }
}
