package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.interfaces.handler.connection.ConnectionMigrateHandler;
import com.co.kc.imchat.broker.sdk.model.dto.ConnectionMigrationDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionMigrateParams;
import com.co.kc.imchat.broker.support.event.model.BrokerEvent;
import com.co.kc.imchat.broker.support.event.model.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionMigrateHandlerTest {

    @Test
    void publishesConnectionEventsWhenMigratingConnections() throws Exception {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        RecordingBrokerEventPublisher eventPublisher = new RecordingBrokerEventPublisher();
        ConnectionMigrateHandler handler = new ConnectionMigrateHandler(connectionRegistry, eventPublisher);
        ConnectionMigrateParams params = new ConnectionMigrateParams(List.of(
                new ConnectionMigrationDTO(1L, "gw-1"),
                new ConnectionMigrationDTO(2L, "gw-2")));

        handler.handle(JsonUtils.toJson(params));

        assertThat(connectionRegistry.find(1L)).extracting("gatewayId").containsExactly("gw-1");
        assertThat(connectionRegistry.find(2L)).extracting("gatewayId").containsExactly("gw-2");
        assertThat(eventPublisher.events())
                .filteredOn(ConnectionRegisteredEvent.class::isInstance)
                .extracting(event -> ((ConnectionRegisteredEvent) event).userId())
                .containsExactly(1L, 2L);
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
