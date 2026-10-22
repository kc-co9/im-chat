package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;

/** Gateway 已注册的事件，携带进程身份、端点及认证主体供 Gossip 同步注册观察。 */
public record GatewayRegisteredEvent(String gatewayId, String host, int port,
                                     String workloadIdentity, LocalDateTime occurredOn)
        implements DomainEvent {

    public GatewayRegisteredEvent(String gatewayId, String host, int port,
                                  String workloadIdentity) {
        this(gatewayId, host, port, workloadIdentity, LocalDateTime.now());
    }
}
