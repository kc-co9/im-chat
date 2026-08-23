package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ConnectionCloseHandler extends AbstractBrokerRpcHandler<ConnectionCloseParams, Void> {
    private final ConnectionRegistry connectionRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final GatewayClient gatewayClient;
    private final BrokerConnectionService brokerConnectionService;
    private final BrokerPeerClient brokerPeerClient;

    public ConnectionCloseHandler(
            ConnectionRegistry connectionRegistry,
            GatewayRegistry gatewayRegistry,
            GatewayClient gatewayClient,
            BrokerConnectionService brokerConnectionService,
            BrokerPeerClient brokerPeerClient
    ) {
        super(BrokerBoltOperation.CLOSE_CONNECTIONS);
        this.connectionRegistry = connectionRegistry;
        this.gatewayRegistry = gatewayRegistry;
        this.gatewayClient = gatewayClient;
        this.brokerConnectionService = brokerConnectionService;
        this.brokerPeerClient = brokerPeerClient;
    }

    @Override
    protected Void process(ConnectionCloseParams params) {
        if (forwardToOwner(params)) {
            return null;
        }
        connectionRegistry.find(params.userId()).forEach(route -> gatewayRegistry.find(route.gatewayId())
                .ifPresent(gateway -> gatewayClient.closeConnections(gateway,
                        new com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams(
                                params.userId(), params.sessionVersion()))));
        return null;
    }

    private boolean forwardToOwner(ConnectionCloseParams params) {
        Optional<BrokerEndpointDTO> owner = brokerConnectionService.decideBroker(params.userId());
        if (owner.isEmpty() || brokerConnectionService.isCurrentBroker(owner.get())) {
            return false;
        }
        brokerPeerClient.closeConnections(owner.get(), params);
        return true;
    }
}
