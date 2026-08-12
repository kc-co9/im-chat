package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.interfaces.handler.connection.ConnectionSyncHandler;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.support.event.publisher.NoopBrokerEventPublisher;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
