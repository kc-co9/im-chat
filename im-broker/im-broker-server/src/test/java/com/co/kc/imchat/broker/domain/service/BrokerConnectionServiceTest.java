package com.co.kc.imchat.broker.domain.service;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.ConnectionMigrationDTO;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.broker.support.event.model.BrokerEvent;
import com.co.kc.imchat.broker.support.event.model.ConnectionRemovedEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import com.co.kc.imchat.broker.support.event.publisher.NoopBrokerEventPublisher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@Tag("realtime-behavior")
class BrokerConnectionServiceTest {
    private static final String BROKER_1_HOST = "10.0.0.1";
    private static final String BROKER_2_HOST = "10.0.0.2";
    private static final int BROKER_PORT = 12200;
    private static final String BROKER_1_ID = brokerId(BROKER_1_HOST, BROKER_PORT);
    private static final String BROKER_2_ID = brokerId(BROKER_2_HOST, BROKER_PORT);

    @Test
    void migratesLocalConnectionsAssignedToAnotherBroker() {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        connectionRegistry.register(1L, "gw-1");
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        BrokerEndpointDTO broker = assignedBrokerForUser(brokerRegistry, 1L);
        BrokerPeerClient peerClient = mock(BrokerPeerClient.class);
        RecordingBrokerEventPublisher eventPublisher = new RecordingBrokerEventPublisher();
        BrokerConnectionService brokerConnectionService = new BrokerConnectionService(connectionRegistry,
                brokerRegistry, brokerProperties("127.0.0.1", BROKER_PORT), peerClient, eventPublisher);
        brokerConnectionService.migrateConnection();

        verify(peerClient).migrateConnections(eq(broker),
                eq(List.of(new ConnectionMigrationDTO(1L, "gw-1"))));
        assertThat(connectionRegistry.find(1L)).isEmpty();
        assertThat(eventPublisher.events())
                .filteredOn(ConnectionRemovedEvent.class::isInstance)
                .singleElement()
                .satisfies(event -> {
                    ConnectionRemovedEvent removedEvent = (ConnectionRemovedEvent) event;
                    assertThat(removedEvent.userId()).isEqualTo(1L);
                    assertThat(removedEvent.gatewayId()).isEqualTo("gw-1");
                });
    }

    @Test
    void doesNotRemoveConnectionUpdatedDuringMigration() {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        connectionRegistry.register(1L, "gw-1");
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        BrokerEndpointDTO broker = assignedBrokerForUser(brokerRegistry, 1L);
        BrokerPeerClient peerClient = mock(BrokerPeerClient.class);
        doAnswer(invocation -> {
            connectionRegistry.register(1L, "gw-1");
            return null;
        }).when(peerClient).migrateConnections(eq(broker), anyList());
        BrokerConnectionService brokerConnectionService = new BrokerConnectionService(connectionRegistry,
                brokerRegistry, brokerProperties("127.0.0.1", BROKER_PORT), peerClient, new NoopBrokerEventPublisher());

        brokerConnectionService.migrateConnection();

        assertThat(connectionRegistry.find(1L))
                .extracting("gatewayId")
                .containsExactly("gw-1");
    }

    @Test
    void selectsOwnerBrokerBySortedBrokerIdAndUserIdHash() {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        brokerRegistry.register(BROKER_2_ID, BROKER_2_HOST, BROKER_PORT);
        brokerRegistry.register(BROKER_1_ID, BROKER_1_HOST, BROKER_PORT);
        BrokerConnectionService brokerConnectionService = new BrokerConnectionService(connectionRegistry, brokerRegistry,
                brokerProperties(BROKER_1_HOST, BROKER_PORT), mock(BrokerPeerClient.class), new NoopBrokerEventPublisher());

        assertThat(brokerConnectionService.decideBroker(1L)).isPresent()
                .get()
                .extracting("brokerId")
                .isEqualTo(expectedBrokerId(1L));
    }

    @Test
    void returnsEmptyOwnerBrokerWhenBrokerSnapshotIsEmpty() {
        BrokerConnectionService brokerConnectionService = new BrokerConnectionService(new InMemoryConnectionRegistry(),
                new InMemoryBrokerRegistry(), brokerProperties(BROKER_1_HOST, BROKER_PORT),
                mock(BrokerPeerClient.class), new NoopBrokerEventPublisher());

        assertThat(brokerConnectionService.decideBroker(1L)).isEmpty();
    }

    @Test
    void identifiesCurrentBroker() {
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        brokerRegistry.register(BROKER_1_ID, BROKER_1_HOST, BROKER_PORT);
        brokerRegistry.register(BROKER_2_ID, BROKER_2_HOST, BROKER_PORT);
        BrokerConnectionService brokerConnectionService = new BrokerConnectionService(new InMemoryConnectionRegistry(),
                brokerRegistry, brokerProperties(expectedBrokerHost(1L), BROKER_PORT), mock(BrokerPeerClient.class),
                new NoopBrokerEventPublisher());

        BrokerEndpointDTO broker = brokerConnectionService.decideBroker(1L).orElseThrow();

        assertThat(brokerConnectionService.isCurrentBroker(broker)).isTrue();
    }

    private BrokerEndpointDTO assignedBrokerForUser(InMemoryBrokerRegistry brokerRegistry, Long userId) {
        brokerRegistry.register(BROKER_1_ID, BROKER_1_HOST, BROKER_PORT);
        brokerRegistry.register(BROKER_2_ID, BROKER_2_HOST, BROKER_PORT);
        return brokerRegistry.list().stream()
                .filter(broker -> broker.brokerId().equals(expectedBrokerId(userId)))
                .findFirst()
                .orElseThrow();
    }

    private String expectedBrokerId(Long userId) {
        return Math.floorMod(String.valueOf(userId).hashCode(), 2) == 0 ? BROKER_1_ID : BROKER_2_ID;
    }

    private String expectedBrokerHost(Long userId) {
        return Math.floorMod(String.valueOf(userId).hashCode(), 2) == 0 ? BROKER_1_HOST : BROKER_2_HOST;
    }

    private BrokerProperties brokerProperties(String host, int port) {
        BrokerProperties properties = new BrokerProperties();
        properties.getInstance().setHost(host);
        properties.getInstance().setPort(port);
        return properties;
    }

    private static String brokerId(String host, int port) {
        return "broker-" + host + "-" + port;
    }

    private static class RecordingBrokerEventPublisher implements BrokerEventPublisher {
        private final List<BrokerEvent> events = new ArrayList<>();

        @Override
        public void publish(BrokerEvent event) {
            events.add(event);
        }

        @Override
        public void publish(List<BrokerEvent> eventList) {
            events.addAll(eventList);
        }

        private List<BrokerEvent> events() {
            return events;
        }
    }
}
