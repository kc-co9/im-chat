package com.co.kc.imchat.broker.interfaces.handler.gateway;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.broker.support.event.model.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.support.event.model.GatewayRemovedEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网关注销 RPC 处理器。
 */
@Component
public class GatewayUnregisterHandler extends AbstractBrokerRpcHandler<GatewayUnregisterParams, Void> {
    private final GatewayRegistry gatewayRegistry;
    private final ConnectionRegistry connectionRegistry;
    private final BrokerEventPublisher brokerEventPublisher;

    public GatewayUnregisterHandler(GatewayRegistry gatewayRegistry,
                                    ConnectionRegistry connectionRegistry,
                                    BrokerEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.UNREGISTER_GATEWAY);
        this.gatewayRegistry = gatewayRegistry;
        this.connectionRegistry = connectionRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(GatewayUnregisterParams params) {
        if (params == null || params.gatewayId() == null || params.gatewayId().isBlank()) {
            return null;
        }
        gatewayRegistry.unregister(params.gatewayId());
        List<UserGatewayDTO> removedCollections = connectionRegistry.sync(params.gatewayId(), List.of()).removed();
        brokerEventPublisher.publish(new ConnectionSyncedEvent(params.gatewayId(), List.of(), removedCollections));
        brokerEventPublisher.publish(new GatewayRemovedEvent(params.gatewayId()));
        return null;
    }
}
