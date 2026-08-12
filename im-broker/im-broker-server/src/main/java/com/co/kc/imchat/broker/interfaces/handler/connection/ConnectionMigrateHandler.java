package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.ConnectionMigrationDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionMigrateParams;
import com.co.kc.imchat.broker.support.event.model.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 用户连接迁移 RPC 处理器。
 */
@Component
public class ConnectionMigrateHandler extends AbstractBrokerRpcHandler<ConnectionMigrateParams, Void> {
    private final ConnectionRegistry connectionRegistry;
    private final BrokerEventPublisher brokerEventPublisher;

    public ConnectionMigrateHandler(ConnectionRegistry connectionRegistry,
                                    BrokerEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.MIGRATE_CONNECTIONS);
        this.connectionRegistry = connectionRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(ConnectionMigrateParams params) {
        if (params == null) {
            return null;
        }
        for (ConnectionMigrationDTO connection : params.connections()) {
            connectionRegistry.register(connection.userId(), connection.gatewayId());
            brokerEventPublisher.publish(new ConnectionRegisteredEvent(connection.userId(), connection.gatewayId()));
        }
        return null;
    }
}
