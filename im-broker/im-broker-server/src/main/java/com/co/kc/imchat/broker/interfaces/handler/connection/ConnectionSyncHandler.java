package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.support.event.model.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网关连接快照同步 RPC 处理器。
 */
@Component
public class ConnectionSyncHandler extends AbstractBrokerRpcHandler<ConnectionSyncParams, Void> {
    private final ConnectionRegistry connectionRegistry;
    private final BrokerEventPublisher brokerEventPublisher;

    public ConnectionSyncHandler(ConnectionRegistry connectionRegistry,
                                 BrokerEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.SYNC_CONNECTIONS);
        this.connectionRegistry = connectionRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(ConnectionSyncParams params) {
        if (params == null || params.gatewayId() == null || params.gatewayId().isBlank()) {
            return null;
        }
        List<Long> userIds = params.userIds() == null ? List.of() : params.userIds();
        List<UserGatewayDTO> removedCollections = connectionRegistry.sync(params.gatewayId(), userIds);
        brokerEventPublisher.publish(new ConnectionSyncedEvent(params.gatewayId(), userIds, removedCollections));
        return null;
    }
}
