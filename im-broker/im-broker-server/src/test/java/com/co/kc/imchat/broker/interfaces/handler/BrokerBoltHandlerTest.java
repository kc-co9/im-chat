package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.application.frame.FrameDispatchAppService;
import com.co.kc.imchat.broker.application.local.LocalGatewayCleanupAppService;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.frame.FrameProcessHandler;
import com.co.kc.imchat.broker.interfaces.handler.gateway.GatewayHeartbeatHandler;
import com.co.kc.imchat.broker.interfaces.handler.gateway.GatewayRegisterHandler;
import com.co.kc.imchat.broker.interfaces.handler.gateway.GatewayUnregisterHandler;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltService;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import com.co.kc.imchat.broker.support.observation.NoopDomainEventPublisher;
import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;
import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.message.facade.MessageService;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BrokerBoltHandlerTest {
    private static final String GATEWAY_IDENTITY = "spiffe://im-chat/gateway/test";

    private LocalGatewayCleanupAppService cleanupService(
            InMemoryGatewayRegistry gatewayRegistry, InMemoryConnectionRegistry connectionRegistry) {
        return new LocalGatewayCleanupAppService(gatewayRegistry, connectionRegistry,
                mock(BrokerGossipAdapter.class), Duration.ofSeconds(1), Duration.ofMillis(100));
    }

    @Test
    void gatewayRegisterHandlerStoresGatewayEndpoint() throws Exception {
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        GatewayRegisterHandler handler = new GatewayRegisterHandler(gatewayRegistry,
                new NoopDomainEventPublisher());
        GatewayRegisterParams command = new GatewayRegisterParams("gw-1", "10.0.0.8", 12201);

        Object response = BrokerRequestChainTestSupport.gatewayChain(gatewayRegistry, handler).handle(requestContext(), JsonUtils.toJson(command));

        assertThat(response).isNull();
        assertThat(handler.service()).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(handler.operation()).isEqualTo(BrokerBoltOperation.REGISTER_GATEWAY.operation());
        assertThat(gatewayRegistry.find(new GatewayId("gw-1"))).isPresent();
    }

    @Test
    void gatewayUnregisterHandlerRemovesGatewayEndpoint() throws Exception {
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "10.0.0.8", 12201, GATEWAY_IDENTITY));
        GatewayUnregisterHandler handler = new GatewayUnregisterHandler(
                cleanupService(gatewayRegistry, new InMemoryConnectionRegistry()),
                new NoopDomainEventPublisher());
        GatewayUnregisterParams command = new GatewayUnregisterParams("gw-1");

        Object response = BrokerRequestChainTestSupport.gatewayChain(gatewayRegistry, handler).handle(requestContext(), JsonUtils.toJson(command));

        assertThat(response).isNull();
        assertThat(handler.service()).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(handler.operation()).isEqualTo(BrokerBoltOperation.UNREGISTER_GATEWAY.operation());
        assertThat(gatewayRegistry.find(new GatewayId("gw-1"))).isEmpty();
    }

    @Test
    void gatewayUnregisterHandlerRemovesGatewayConnections() throws Exception {
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        RecordingDomainEventPublisher eventPublisher = new RecordingDomainEventPublisher();
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "10.0.0.8", 12201, GATEWAY_IDENTITY));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(2L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-2")), new RouteRevision(0L), Instant.MAX));
        GatewayUnregisterHandler handler = new GatewayUnregisterHandler(
                cleanupService(gatewayRegistry, connectionRegistry), eventPublisher);

        BrokerRequestChainTestSupport.gatewayChain(gatewayRegistry, handler).handle(requestContext(), JsonUtils.toJson(new GatewayUnregisterParams("gw-1")));

        assertThat(connectionRegistry.find(new UserId(1L)))
                .extracting(registration -> registration.gatewayId().value())
                .containsExactly("gw-2");
        assertThat(connectionRegistry.find(new UserId(2L))).isEmpty();
        assertThat(eventPublisher.gatewayId()).isEqualTo("gw-1");
        assertThat(eventPublisher.userIds()).isEmpty();
        assertThat(eventPublisher.removedLocations())
                .extracting(registration -> registration.userId().value())
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void gatewayHeartbeatHandlerRefreshesGatewayEndpoint() throws Exception {
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "10.0.0.8", 12201, GATEWAY_IDENTITY));
        GatewayRegistration before = gatewayRegistry.find(new GatewayId("gw-1")).orElseThrow();
        GatewayHeartbeatHandler handler = new GatewayHeartbeatHandler(gatewayRegistry,
                new NoopDomainEventPublisher());

        Object response = BrokerRequestChainTestSupport.gatewayChain(gatewayRegistry, handler).handle(requestContext(),
                JsonUtils.toJson(new GatewayHeartbeatParams("gw-1")));

        assertThat(response).isNull();
        assertThat(handler.service()).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(handler.operation()).isEqualTo(BrokerBoltOperation.HEARTBEAT_GATEWAY.operation());
        assertThat(gatewayRegistry.find(new GatewayId("gw-1")))
                .get()
                .extracting(endpoint -> endpoint.lastSeenAt().isAfter(before.lastSeenAt()))
                .isEqualTo(true);
    }

    private BoltRequestContext requestContext() {
        return new BoltRequestContext("127.0.0.1:12345", "127.0.0.1", GATEWAY_IDENTITY, false, Set.of());
    }

    @Test
    void frameProcessHandlerReturnsBrokerFrameResponse() throws Exception {
        RecordingFrameProcessHandler handler = new RecordingFrameProcessHandler();
        handler.processFrameResponse = BrokerFrameWriteResult.ok();
        BrokerFrameWriteParams command = BrokerFrameWriteParams.inbound(1L, "conn-1",
                new FrameRequest("1", "message.private.send", "1", "trace", Map.of()));

        Object response = BrokerRequestChainTestSupport.passThroughChain(handler).handle(null, JsonUtils.toJson(command));

        assertThat(response).isEqualTo(BrokerFrameWriteResult.ok());
    }

    private static class RecordingFrameProcessHandler extends FrameProcessHandler {
        private BrokerFrameWriteResult processFrameResponse;

        RecordingFrameProcessHandler() {
            super(unusedRemoteService(MessageService.class),
                    mock(FrameDispatchAppService.class));
        }

        @Override
        protected BrokerFrameWriteResult process(BrokerFrameWriteParams command) {
            return processFrameResponse;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T unusedRemoteService(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException("unused remote service");
                });
    }

    private static BoltInvoker unusedBoltInvoker() {
        return new BoltInvoker() {
            @Override
            public <T, R> R invokeAuthenticated(String address, String service, String operation,
                                               T request, Class<R> responseType, int timeoutMillis,
                                               String expectedPeerIdentity, String identityPrefix) {
                throw new AssertionError("ordinary data plane must not request authenticated observation RPC");
            }

            @Override
            public <T, R> R invoke(String address, String service, String operation, T request,
                                   Class<R> responseType, int timeoutMillis) {
                throw new UnsupportedOperationException("unused bolt invoker");
            }
        };
    }

    private static class RecordingDomainEventPublisher
            extends NoopDomainEventPublisher {
        private String gatewayId;
        private List<Long> userIds = List.of();
        private List<ConnectionRegistration> removedLocations = List.of();

        @Override
        public void publish(DomainEvent event) {
            if (event instanceof ConnectionSyncedEvent synced) {
                this.gatewayId = synced.gatewayId();
                this.userIds = synced.userIds();
                this.removedLocations = synced.removedLocations();
            }
        }

        private String gatewayId() {
            return gatewayId;
        }

        private List<Long> userIds() {
            return userIds;
        }

        private List<ConnectionRegistration> removedLocations() {
            return removedLocations;
        }
    }
}
