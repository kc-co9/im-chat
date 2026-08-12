package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.broker.support.event.model.ConnectionRemovedEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 用户连接注销 RPC 处理器。
 */
@Component
public class ConnectionUnregisterHandler extends AbstractBrokerRpcHandler<ConnectionUnregisterParams, Void> {
    private final ConnectionRegistry connectionRegistry;
    private final BrokerEventPublisher brokerEventPublisher;
    private final BrokerConnectionService brokerConnectionService;
    private final BrokerPeerClient brokerPeerClient;

    public ConnectionUnregisterHandler(ConnectionRegistry connectionRegistry,
                                       BrokerEventPublisher brokerEventPublisher,
                                       BrokerConnectionService brokerConnectionService,
                                       BrokerPeerClient brokerPeerClient) {
        super(BrokerBoltOperation.UNREGISTER_CONNECTION);
        this.connectionRegistry = connectionRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
        this.brokerConnectionService = brokerConnectionService;
        this.brokerPeerClient = brokerPeerClient;
    }

    @Override
    protected Void process(ConnectionUnregisterParams params) {
        if (forwardToOwner(params)) {
            return null;
        }
        connectionRegistry.unregister(params.userId(), params.gatewayId());
        brokerEventPublisher.publish(new ConnectionRemovedEvent(params.userId(), params.gatewayId()));
        return null;
    }

    private boolean forwardToOwner(ConnectionUnregisterParams request) {
        Optional<BrokerEndpointDTO> owner = brokerConnectionService.decideBroker(request.userId());
        if (owner.isEmpty() || brokerConnectionService.isCurrentBroker(owner.get())) {
            return false;
        }
        brokerPeerClient.unregisterConnection(owner.get(), request);
        return true;
    }
}
