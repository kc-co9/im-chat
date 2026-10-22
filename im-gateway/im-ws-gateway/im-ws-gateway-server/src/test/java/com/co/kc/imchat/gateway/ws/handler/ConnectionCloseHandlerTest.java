package com.co.kc.imchat.gateway.ws.handler;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import com.co.kc.imchat.gateway.ws.delivery.ConnectionCloseDeduplicator;
import com.co.kc.imchat.gateway.ws.security.GatewayBrokerAuthorizer;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConnectionCloseHandlerTest {

    @Test
    void rejectsCloseWithoutTransportContext() {
        ConnectionCloseHandler handler = new ConnectionCloseHandler(new ConnectionRegistry(),
                new ConnectionCloseDeduplicator(
                        10, Duration.ofMinutes(1), System::currentTimeMillis),
                trustedNetworkAuthorizer());

        assertThatThrownBy(() -> handler.handle(null, "{}"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void closesOnlyMatchingVersionConnections() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel oldConnection = new EmbeddedChannel();
        EmbeddedChannel newConnection = new EmbeddedChannel();
        registry.register(1L, "session-old", "conn-old", oldConnection);
        registry.register(1L, "session-new", "conn-new", newConnection);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(registry,
                new ConnectionCloseDeduplicator(
                        10, Duration.ofMinutes(1), System::currentTimeMillis),
                trustedNetworkAuthorizer());

        ConnectionCloseParams close = new ConnectionCloseParams(
                "operation-1", 1L, "session-old");
        handler.handle(requestContext(), JsonUtils.toJson(close));
        handler.handle(requestContext(), JsonUtils.toJson(close));

        assertThat(handler.service()).isEqualTo(GatewayBoltService.CONNECTION.service());
        assertThat(handler.operation()).isEqualTo(GatewayBoltOperation.CLOSE_CONNECTIONS.operation());
        assertThat(oldConnection.isOpen()).isFalse();
        assertThat(newConnection.isOpen()).isTrue();
    }

    @Test
    void retriesCloseAfterRegistryFailure() {
        ConnectionRegistry registry = mock(ConnectionRegistry.class);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(registry,
                new ConnectionCloseDeduplicator(
                        10, Duration.ofMinutes(1), System::currentTimeMillis),
                trustedNetworkAuthorizer());
        ConnectionCloseParams close = new ConnectionCloseParams("operation-1", 1L, "session-old");
        doThrow(new IllegalStateException("close failed"))
                .doReturn(java.util.List.of("conn-old"))
                .when(registry).closeConnections(1L, "session-old");

        assertThatThrownBy(() -> handler.handle(requestContext(), JsonUtils.toJson(close)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("close failed");
        handler.handle(requestContext(), JsonUtils.toJson(close));

        verify(registry, org.mockito.Mockito.times(2)).closeConnections(1L, "session-old");
    }

    @Test
    void doesNotAcknowledgeCloseStillInProgress() {
        ConnectionRegistry registry = mock(ConnectionRegistry.class);
        ConnectionCloseDeduplicator deduplicator = new ConnectionCloseDeduplicator(
                10, Duration.ofMinutes(1), System::currentTimeMillis);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(
                registry, deduplicator, trustedNetworkAuthorizer());
        ConnectionCloseParams close = new ConnectionCloseParams("operation-1", 1L, "session-old");
        assertThat(deduplicator.acquire("operation-1", 1L, "session-old"))
                .isEqualTo(ConnectionCloseDeduplicator.Decision.ACQUIRED);

        assertThatThrownBy(() -> handler.handle(requestContext(), JsonUtils.toJson(close)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("connection close is still in progress");
        org.mockito.Mockito.verifyNoInteractions(registry);
    }

    private GatewayBrokerAuthorizer trustedNetworkAuthorizer() {
        return new GatewayBrokerAuthorizer(() -> {
            throw new AssertionError("trusted network fallback must not load cluster state");
        }, "spiffe://im-chat/broker/", true);
    }

    private BoltRequestContext requestContext() {
        return new BoltRequestContext("127.0.0.1:18020", "127.0.0.1", null, false, Set.of());
    }
}
