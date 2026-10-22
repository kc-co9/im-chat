package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.common.domain.user.model.UserId;

import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import org.springframework.stereotype.Component;

/** 按本机用户路由向已注册 Gateway 发送关闭控制，透传操作标识和会话版本供网关去重与筛选。 */
@Component
public class ConnectionCloseHandler extends
        AbstractBrokerRpcHandler<ConnectionCloseParams, RouteBrokerResult> {
    private final ConnectionRegistry connectionRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final GatewayClient gatewayClient;

    public ConnectionCloseHandler(
            ConnectionRegistry connectionRegistry,
            GatewayRegistry gatewayRegistry,
            GatewayClient gatewayClient
    ) {
        super(BrokerBoltOperation.CLOSE_CONNECTIONS);
        this.connectionRegistry = connectionRegistry;
        this.gatewayRegistry = gatewayRegistry;
        this.gatewayClient = gatewayClient;
    }

    @Override
    protected Object process(ConnectionCloseParams params) {
        connectionRegistry.find(new UserId(params.userId()))
                .forEach(route -> gatewayRegistry.find(route.gatewayId())
                        .ifPresent(gateway ->
                                gatewayClient.closeConnections(RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateway),
                                        new com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams(
                                                params.routeContext().operationId(), params.userId(), params.sessionVersion()))));
        return null;
    }
}
