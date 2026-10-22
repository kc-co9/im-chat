package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 用户连接注册 RPC 处理器。
 */
@Component
public class ConnectionRegisterHandler extends
        AbstractBrokerRpcHandler<ConnectionRegisterParams, RouteBrokerResult> {
    private final ConnectionRegistry connectionRegistry;
    private final DomainEventPublisher brokerEventPublisher;

    public ConnectionRegisterHandler(ConnectionRegistry connectionRegistry,
                                     DomainEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.REGISTER_CONNECTION);
        this.connectionRegistry = connectionRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Object process(ConnectionRegisterParams params) {
        ConnectionRegistration registration = ConnectionRegistration.active(
                new ConnectionRouteId(new UserId(params.userId()), new GatewayId(params.gatewayId())),
                new RouteRevision(params.routeRevision()), Instant.ofEpochMilli(params.routeContext().expireAtMs()));
        boolean accepted = connectionRegistry.register(registration);
        if (accepted) {
            brokerEventPublisher.publish(
                    new ConnectionRegisteredEvent(params.userId(), params.gatewayId()));
        }
        return null;
    }
}
