package com.co.kc.imchat.broker.support.client;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.ConnectionMigrationDTO;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionMigrateParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.plugin.bolt.core.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Broker 实例间点对点调用客户端。
 */
@Component
public class BrokerPeerClient {
    private final BoltRpcClient boltRpcClient;
    private final int timeoutMillis;

    public BrokerPeerClient(BoltInvoker boltInvoker, BrokerProperties properties) {
        this.boltRpcClient = new BoltRpcClient(boltInvoker);
        this.timeoutMillis = properties.getPeerCall().getTimeoutMillis();
    }

    public void registerConnection(BrokerEndpointDTO broker, ConnectionRegisterParams params) {
        invoke(broker, BrokerBoltOperation.REGISTER_CONNECTION, params, Void.class);
    }

    public void unregisterConnection(BrokerEndpointDTO broker, ConnectionUnregisterParams params) {
        invoke(broker, BrokerBoltOperation.UNREGISTER_CONNECTION, params, Void.class);
    }

    public BrokerFrameWriteResult writeFrame(BrokerEndpointDTO broker, BrokerFrameWriteParams params) {
        return invoke(broker, BrokerBoltOperation.WRITE_FRAME, params, BrokerFrameWriteResult.class);
    }

    public void migrateConnections(BrokerEndpointDTO broker, List<ConnectionMigrationDTO> connections) {
        invoke(broker, BrokerBoltOperation.MIGRATE_CONNECTIONS, new ConnectionMigrateParams(connections), Void.class);
    }

    private <T, R> R invoke(BrokerEndpointDTO broker, BrokerBoltOperation operation,
                            T request, Class<R> responseType) {
        return boltRpcClient.invoke(addressOf(broker), operation.service().service(), operation.operation(),
                request, responseType, timeoutMillis);
    }

    private String addressOf(BrokerEndpointDTO broker) {
        return broker.host() + ":" + broker.port();
    }
}
