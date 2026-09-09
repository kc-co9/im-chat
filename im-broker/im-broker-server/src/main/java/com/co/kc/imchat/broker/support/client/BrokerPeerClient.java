package com.co.kc.imchat.broker.support.client;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.ConnectionMigrationDTO;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionMigrateParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionMigrationRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.enums.DiagnosticStatus;
import com.co.kc.imchat.broker.support.diagnostic.tracker.impl.ConnectionMigrationTracker;
import com.co.kc.imchat.plugin.bolt.core.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Broker 实例间点对点调用客户端。
 */
@Slf4j
@Component
public class BrokerPeerClient {
    private final BoltRpcClient boltRpcClient;
    private final int timeoutMillis;
    private final ConnectionMigrationTracker connectionMigrationTracker;

    public BrokerPeerClient(
            BoltInvoker boltInvoker,
            BrokerProperties properties,
            ConnectionMigrationTracker connectionMigrationTracker
    ) {
        this.boltRpcClient = new BoltRpcClient(boltInvoker);
        this.timeoutMillis = properties.getPeerCall().getTimeoutMillis();
        this.connectionMigrationTracker = connectionMigrationTracker;
    }

    public void registerConnection(BrokerEndpointDTO broker, ConnectionRegisterParams params) {
        invoke(broker, BrokerBoltOperation.REGISTER_CONNECTION, params, Void.class);
    }

    public void unregisterConnection(BrokerEndpointDTO broker, ConnectionUnregisterParams params) {
        invoke(broker, BrokerBoltOperation.UNREGISTER_CONNECTION, params, Void.class);
    }

    public void closeConnections(BrokerEndpointDTO broker, ConnectionCloseParams params) {
        invoke(broker, BrokerBoltOperation.CLOSE_CONNECTIONS, params, Void.class);
    }

    public BrokerFrameWriteResult writeFrame(BrokerEndpointDTO broker, BrokerFrameWriteParams params) {
        return invoke(broker, BrokerBoltOperation.WRITE_FRAME, params, BrokerFrameWriteResult.class);
    }

    public void migrateConnections(BrokerEndpointDTO broker, List<ConnectionMigrationDTO> connections) {
        Instant startedAt = Instant.now();
        try {
            invoke(broker, BrokerBoltOperation.MIGRATE_CONNECTIONS,
                    new ConnectionMigrateParams(connections), Void.class);
            trackMigration(broker, connections.size(), startedAt, DiagnosticStatus.SUCCESS, null);
        } catch (RuntimeException exception) {
            trackMigration(broker, 0, startedAt, DiagnosticStatus.FAILED,
                    exception.getMessage());
            throw exception;
        }
    }

    private void trackMigration(
            BrokerEndpointDTO broker,
            Integer processedCount,
            Instant startedAt,
            DiagnosticStatus status,
            String errorSummary
    ) {
        try {
            Instant completedAt = Instant.now();
            connectionMigrationTracker.track(new ConnectionMigrationRecordDTO(
                    completedAt,
                    broker.brokerId(),
                    status,
                    processedCount,
                    Duration.between(startedAt, completedAt).toMillis(),
                    errorSummary));
        } catch (RuntimeException exception) {
            log.warn("failed to track connection migration, broker:{}, error:{}",
                    broker.brokerId(), exception.toString());
        }
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
