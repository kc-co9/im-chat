package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.interfaces.handler.connection.ConnectionSyncHandler;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.support.event.publisher.NoopBrokerEventPublisher;
import com.co.kc.imchat.broker.support.event.model.BrokerEvent;
import com.co.kc.imchat.broker.support.event.model.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionSyncHandlerTest {

    @Test
    void syncRemovesStaleGatewayConnections() throws Exception {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        ConnectionSyncHandler handler = new ConnectionSyncHandler(connectionRegistry, new NoopBrokerEventPublisher());
        connectionRegistry.register(1L, "gw-1");
        connectionRegistry.register(2L, "gw-1");
        connectionRegistry.register(1L, "gw-2");

        handler.handle(JsonUtils.toJson(new ConnectionSyncParams("gw-1", List.of(2L))));

        assertEquals(List.of("gw-2"), connectionRegistry.find(1L).stream()
                .map(UserGatewayDTO::gatewayId)
                .sorted()
                .toList());
        assertEquals(List.of("gw-1"), connectionRegistry.find(2L).stream()
                .map(UserGatewayDTO::gatewayId)
                .toList());
    }

    @Test
    void syncRestoresGatewayConnectionsWhenBrokerStateIsEmpty() throws Exception {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        RecordingBrokerEventPublisher eventPublisher = new RecordingBrokerEventPublisher();
        ConnectionSyncHandler handler = new ConnectionSyncHandler(connectionRegistry, eventPublisher);

        handler.handle(JsonUtils.toJson(new ConnectionSyncParams("gw-1", List.of(1L, 2L))));

        assertEquals(List.of("gw-1"), connectionRegistry.find(1L).stream()
                .map(UserGatewayDTO::gatewayId)
                .toList());
        assertEquals(List.of("gw-1"), connectionRegistry.find(2L).stream()
                .map(UserGatewayDTO::gatewayId)
                .toList());
        assertEquals(List.of(1L, 2L), eventPublisher.events.stream()
                .filter(ConnectionRegisteredEvent.class::isInstance)
                .map(ConnectionRegisteredEvent.class::cast)
                .map(ConnectionRegisteredEvent::userId)
                .sorted()
                .toList());
    }

    @Test
    void syncWithNullUserIdsRemovesGatewayConnections() throws Exception {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        ConnectionSyncHandler handler = new ConnectionSyncHandler(connectionRegistry, new NoopBrokerEventPublisher());
        connectionRegistry.register(1L, "gw-1");
        connectionRegistry.register(1L, "gw-2");

        handler.handle(JsonUtils.toJson(new ConnectionSyncParams("gw-1", null)));

        assertEquals(List.of("gw-2"), connectionRegistry.find(1L).stream()
                .map(UserGatewayDTO::gatewayId)
                .toList());
    }

    @Test
    void syncIgnoresBlankGatewayId() {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        ConnectionSyncHandler handler = new ConnectionSyncHandler(connectionRegistry, new NoopBrokerEventPublisher());

        assertThatCode(() -> handler.handle(JsonUtils.toJson(new ConnectionSyncParams(" ", List.of()))))
                .doesNotThrowAnyException();
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
    }
}
