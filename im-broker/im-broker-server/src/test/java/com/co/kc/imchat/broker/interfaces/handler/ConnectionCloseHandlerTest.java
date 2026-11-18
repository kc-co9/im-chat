package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.connection.ConnectionCloseHandler;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConnectionCloseHandlerTest {

    @Test
    void forwardsCloseConditionToCurrentUserGateway() throws Exception {
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(2L), new GatewayId("gw-2")), new RouteRevision(0L), Instant.MAX));
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "10.0.0.1", 12201, "test-identity"));
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-2", "10.0.0.2", 12201, "test-identity"));
        GatewayClient gatewayClient = mock(GatewayClient.class);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(
                connections,
                gateways,
                gatewayClient);
        ConnectionCloseParams params = params(1L);

        BrokerRequestChainTestSupport.acceptingChain(handler).handle(null, JsonUtils.toJson(params));

        GatewayRegistration gateway = gateways.find(new GatewayId("gw-1")).orElseThrow();
        verify(gatewayClient).closeConnections(
                RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateway), new com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams(
                        "close-1", 1L, "session-old"));
        verify(gatewayClient, never()).closeConnections(
                RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateways.find(new GatewayId("gw-2")).orElseThrow()),
                new com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams(
                        "close-1", 1L, "session-old"));
    }

    @Test
    void missingUserRouteIsSuccessfulNoOp() {
        GatewayClient gatewayClient = mock(GatewayClient.class);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(
                new InMemoryConnectionRegistry(),
                new InMemoryGatewayRegistry(),
                gatewayClient);

        assertThatCode(() -> BrokerRequestChainTestSupport.acceptingChain(handler).handle(null, JsonUtils.toJson(params(1L))))
                .doesNotThrowAnyException();

        verify(gatewayClient, never()).closeConnections(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private ConnectionCloseParams params(Long userId) {
        return new ConnectionCloseParams(userId, "session-old",
                new RouteRequestContext(BrokerRequestChainTestSupport.EPOCH,
                        "close-" + userId, System.currentTimeMillis() + 10_000L));
    }
}
