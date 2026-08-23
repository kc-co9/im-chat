package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.interfaces.handler.connection.ConnectionCloseHandler;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConnectionCloseHandlerTest {

    @Test
    void forwardsCloseConditionToCurrentUserGateway() throws Exception {
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        connections.register(1L, "gw-1");
        connections.register(2L, "gw-2");
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        gateways.register("gw-1", "10.0.0.1", 12201);
        gateways.register("gw-2", "10.0.0.2", 12201);
        GatewayClient gatewayClient = mock(GatewayClient.class);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(
                connections,
                gateways,
                gatewayClient,
                mock(BrokerConnectionService.class),
                mock(BrokerPeerClient.class));
        ConnectionCloseParams params = new ConnectionCloseParams(1L, "session-old");

        handler.handle(JsonUtils.toJson(params));

        GatewayEndpointDTO gateway = gateways.find("gw-1").orElseThrow();
        verify(gatewayClient).closeConnections(
                gateway, new com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams(
                        1L, "session-old"));
        verify(gatewayClient, never()).closeConnections(
                gateways.find("gw-2").orElseThrow(),
                new com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams(1L, "session-old"));
    }

    @Test
    void missingUserRouteIsSuccessfulNoOp() {
        GatewayClient gatewayClient = mock(GatewayClient.class);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(
                new InMemoryConnectionRegistry(),
                new InMemoryGatewayRegistry(),
                gatewayClient,
                mock(BrokerConnectionService.class),
                mock(BrokerPeerClient.class));

        assertThatCode(() -> handler.handle(JsonUtils.toJson(new ConnectionCloseParams(1L, "session-old"))))
                .doesNotThrowAnyException();

        verify(gatewayClient, never()).closeConnections(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void forwardsCloseCommandToOwningBroker() throws Exception {
        GatewayClient gatewayClient = mock(GatewayClient.class);
        BrokerConnectionService brokerConnectionService = mock(BrokerConnectionService.class);
        BrokerPeerClient brokerPeerClient = mock(BrokerPeerClient.class);
        BrokerEndpointDTO owner = new BrokerEndpointDTO(
                "broker-2", "10.0.0.2", 12200, Instant.now(), Instant.now());
        ConnectionCloseParams params = new ConnectionCloseParams(1L, "session-old");
        when(brokerConnectionService.decideBroker(1L)).thenReturn(Optional.of(owner));
        when(brokerConnectionService.isCurrentBroker(owner)).thenReturn(false);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(
                new InMemoryConnectionRegistry(),
                new InMemoryGatewayRegistry(),
                gatewayClient,
                brokerConnectionService,
                brokerPeerClient);

        handler.handle(JsonUtils.toJson(params));

        verify(brokerPeerClient).closeConnections(owner, params);
        verifyNoInteractions(gatewayClient);
    }
}
