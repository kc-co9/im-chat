package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.broker.support.event.model.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 用户连接注册 RPC 处理器。
 */
@Component
public class ConnectionRegisterHandler extends AbstractBrokerRpcHandler<ConnectionRegisterParams, Void> {
    private final ConnectionRegistry connectionRegistry;
    private final BrokerEventPublisher brokerEventPublisher;
    private final BrokerConnectionService brokerConnectionService;
    private final BrokerPeerClient brokerPeerClient;

    public ConnectionRegisterHandler(ConnectionRegistry connectionRegistry,
                                     BrokerEventPublisher brokerEventPublisher,
                                     BrokerConnectionService brokerConnectionService,
                                     BrokerPeerClient brokerPeerClient) {
        super(BrokerBoltOperation.REGISTER_CONNECTION);
        this.connectionRegistry = connectionRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
        this.brokerConnectionService = brokerConnectionService;
        this.brokerPeerClient = brokerPeerClient;
    }

    @Override
    protected Void process(ConnectionRegisterParams params) {
        if (forwardToOwner(params)) {
            return null;
        }
        connectionRegistry.register(params.userId(), params.gatewayId());
        brokerEventPublisher.publish(new ConnectionRegisteredEvent(params.userId(), params.gatewayId()));
        return null;
    }

    private boolean forwardToOwner(ConnectionRegisterParams params) {
        Optional<BrokerEndpointDTO> owner = brokerConnectionService.decideBroker(params.userId());
        if (owner.isEmpty() || brokerConnectionService.isCurrentBroker(owner.get())) {
            return false;
        }
        brokerPeerClient.registerConnection(owner.get(), params);
        return true;
    }
}
