package com.co.kc.imchat.gateway.ws.handler;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;

public class ConnectionCloseHandler implements BoltRequestHandler {
    private final ConnectionRegistry connectionRegistry;

    public ConnectionCloseHandler(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
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
    public Object handle(String payload) {
        ConnectionCloseParams params = JsonUtils.fromJson(payload, ConnectionCloseParams.class);
        connectionRegistry.closeConnections(params.userId(), params.sessionVersion());
        return null;
    }
}
