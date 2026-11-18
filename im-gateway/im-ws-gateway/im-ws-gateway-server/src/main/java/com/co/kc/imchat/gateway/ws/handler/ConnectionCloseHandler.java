package com.co.kc.imchat.gateway.ws.handler;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import com.co.kc.imchat.gateway.ws.delivery.ConnectionCloseDeduplicator;
import com.co.kc.imchat.gateway.ws.security.GatewayBrokerAuthorizer;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;

public class ConnectionCloseHandler implements BoltRequestHandler {
    private final ConnectionRegistry connectionRegistry;
    private final ConnectionCloseDeduplicator deduplicator;
    private final GatewayBrokerAuthorizer authorizer;

    public ConnectionCloseHandler(ConnectionRegistry connectionRegistry,
                                  ConnectionCloseDeduplicator deduplicator,
                                  GatewayBrokerAuthorizer authorizer) {
        this.connectionRegistry = connectionRegistry;
        this.deduplicator = deduplicator;
        this.authorizer = authorizer;
    }

    @Override
    public String service() {
        return GatewayBoltService.CONNECTION.service();
    }

    @Override
    public String operation() {
        return GatewayBoltOperation.CLOSE_CONNECTIONS.operation();
    }

    @Override
    public Object handle(BoltRequestContext context, String payload) {
        authorizer.authorize(context);
        ConnectionCloseParams params = JsonUtils.fromJson(payload, ConnectionCloseParams.class);
        ConnectionCloseDeduplicator.Decision decision = deduplicator.acquire(
                params.operationId(), params.userId(), params.sessionVersion());
        if (decision == ConnectionCloseDeduplicator.Decision.CAPACITY_EXHAUSTED) {
            throw new IllegalStateException("connection close deduplication capacity exhausted");
        }
        if (decision == ConnectionCloseDeduplicator.Decision.DUPLICATE) {
            return null;
        }
        if (decision == ConnectionCloseDeduplicator.Decision.IN_PROGRESS) {
            throw new IllegalStateException("connection close is still in progress");
        }
        try {
            connectionRegistry.closeConnections(params.userId(), params.sessionVersion());
            deduplicator.complete(params.operationId(), params.userId(), params.sessionVersion());
        } catch (RuntimeException | Error failure) {
            deduplicator.release(params.operationId(), params.userId(), params.sessionVersion());
            throw failure;
        }
        return null;
    }
}
