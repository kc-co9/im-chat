package com.co.kc.imchat.broker.support.client;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.ConnectionMigrationDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionMigrateParams;
import com.co.kc.imchat.broker.support.diagnostic.model.enums.DiagnosticStatus;
import com.co.kc.imchat.broker.support.diagnostic.tracker.impl.ConnectionMigrationTracker;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BrokerPeerClientTest {

    @Test
    void wrapsMigrationConnectionsInRpcParams() {
        BoltInvoker boltInvoker = mock(BoltInvoker.class);
        BrokerProperties properties = new BrokerProperties();
        ConnectionMigrationTracker tracker = new ConnectionMigrationTracker(10);
        BrokerPeerClient client = new BrokerPeerClient(boltInvoker, properties, tracker);
        BrokerEndpointDTO broker = new BrokerEndpointDTO(
                "broker-1", "10.0.0.1", 12200, Instant.now(), Instant.now());
        List<ConnectionMigrationDTO> connections = List.of(
                new ConnectionMigrationDTO(1L, "gw-1"));
        ArgumentCaptor<ConnectionMigrateParams> paramsCaptor =
                ArgumentCaptor.forClass(ConnectionMigrateParams.class);

        client.migrateConnections(broker, connections);

        verify(boltInvoker).invoke(
                eq("10.0.0.1:12200"),
                eq("broker.connection"),
                eq("migrateConnections"),
                paramsCaptor.capture(),
                eq(Void.class),
                eq(properties.getPeerCall().getTimeoutMillis()));
        assertThat(paramsCaptor.getValue().connections()).containsExactlyElementsOf(connections);
        assertThat(tracker.recent(10)).singleElement().satisfies(record -> {
            assertThat(record.targetBrokerId()).isEqualTo("broker-1");
            assertThat(record.status()).isEqualTo(DiagnosticStatus.SUCCESS);
            assertThat(record.processedCount()).isEqualTo(1);
            assertThat(record.errorSummary()).isNull();
        });
    }

    @Test
    void tracksFailedMigrationAndPreservesRpcException() {
        BoltInvoker boltInvoker = mock(BoltInvoker.class);
        BrokerProperties properties = new BrokerProperties();
        ConnectionMigrationTracker tracker = new ConnectionMigrationTracker(10);
        BrokerPeerClient client = new BrokerPeerClient(boltInvoker, properties, tracker);
        BrokerEndpointDTO broker = new BrokerEndpointDTO(
                "broker-1", "10.0.0.1", 12200, Instant.now(), Instant.now());
        List<ConnectionMigrationDTO> connections = List.of(
                new ConnectionMigrationDTO(1L, "gw-1"));
        IllegalStateException failure = new IllegalStateException("peer unavailable");
        doThrow(failure).when(boltInvoker).invoke(
                eq("10.0.0.1:12200"),
                eq("broker.connection"),
                eq("migrateConnections"),
                any(ConnectionMigrateParams.class),
                eq(Void.class),
                eq(properties.getPeerCall().getTimeoutMillis()));

        assertThatThrownBy(() -> client.migrateConnections(broker, connections))
                .isSameAs(failure);

        assertThat(tracker.recent(10)).singleElement().satisfies(record -> {
            assertThat(record.targetBrokerId()).isEqualTo("broker-1");
            assertThat(record.status()).isEqualTo(DiagnosticStatus.FAILED);
            assertThat(record.processedCount()).isZero();
            assertThat(record.errorSummary()).isEqualTo("peer unavailable");
        });
    }
}
