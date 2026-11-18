package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRemovedEvent;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 用户连接注销 RPC 处理器。
 */
@Component
public class ConnectionUnregisterHandler extends
        AbstractBrokerRpcHandler<ConnectionUnregisterParams, RouteBrokerResult> {
    private final ConnectionRegistry connectionRegistry;
    private final DomainEventPublisher brokerEventPublisher;

    public ConnectionUnregisterHandler(ConnectionRegistry connectionRegistry,
                                       DomainEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.UNREGISTER_CONNECTION);
        this.connectionRegistry = connectionRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Object process(ConnectionUnregisterParams params) {
        boolean accepted = connectionRegistry.unregister(
                new ConnectionRouteId(new UserId(params.userId()), new GatewayId(params.gatewayId())), new RouteRevision(params.routeRevision()),
                Instant.ofEpochMilli(params.routeContext().expireAtMs()));
        if (accepted) {
            brokerEventPublisher.publish(
                    new ConnectionRemovedEvent(params.userId(), params.gatewayId()));
        }
        return null;
    }
}
