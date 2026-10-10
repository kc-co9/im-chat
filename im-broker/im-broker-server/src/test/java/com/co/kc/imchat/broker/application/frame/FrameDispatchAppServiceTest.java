package com.co.kc.imchat.broker.application.frame;


import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;

import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;

import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.support.RouteLookupStub;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;

import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class FrameDispatchAppServiceTest {

    @Test
    void invalidDeliveryInputPropagatesBeforeLookupOrGatewayWrite() {
        AtomicInteger lookups = new AtomicInteger();
        RecordingGatewayClient gatewayClient = new RecordingGatewayClient();
        FrameDispatchAppService service = service(new InMemoryConnectionRegistry(), gateways(),
                lookupService(lookups, List.of()), gatewayClient);

        assertThatThrownBy(() -> service.dispatch(new FrameDispatchCommand(
                null, frame(), "delivery-1", 7L)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.dispatch(new FrameDispatchCommand(
                42L, null, "delivery-1", 7L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.dispatch(new FrameDispatchCommand(
                42L, frame(), " ", 7L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(lookups).hasValue(0);
        assertThat(gatewayClient.requests).isEmpty();
    }

    @Test
    void localHitDoesNotRunRouteLookup() {
        AtomicInteger lookups = new AtomicInteger();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        InMemoryGatewayRegistry gateways = gateways();
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(42L), new GatewayId("gateway-a")), new RouteRevision(0L), Instant.MAX));
        RecordingGatewayClient gatewayClient = new RecordingGatewayClient();
        FrameDispatchAppService service = service(connections, gateways,
                lookupService(lookups, List.of()), gatewayClient);

        BrokerFrameWriteResult result = service.dispatch(new FrameDispatchCommand(
                42L, frame(), "delivery-1", 7L));

        assertThat(lookups).hasValue(0);
        assertThat(gatewayClient.requests).singleElement()
                .extracting(GatewayFrameWriteParams::deliveryId)
                .isEqualTo("delivery-1");
        assertThat(result.acceptedConnectionIds()).containsExactly("connection-a");
    }

    @Test
    void gatewayWriteFailureDoesNotAcceptConnections() {
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(42L), new GatewayId("gateway-a")), new RouteRevision(0L), Instant.MAX));
        GatewayClient gatewayClient = mock(GatewayClient.class);
        when(gatewayClient.writeFrame(any(), any())).thenThrow(new IllegalStateException("gateway unavailable"));
        FrameDispatchAppService service = service(connections, gateways(),
                lookupService(new AtomicInteger(), List.of()), gatewayClient);

        BrokerFrameWriteResult result = service.dispatch(new FrameDispatchCommand(
                42L, frame(), "delivery-failed", 7L));

        assertThat(result.acceptedConnectionIds()).isEmpty();
    }

    @Test
    void writesToGatewaysConcurrentlyAndKeepsSuccessfulConnections() throws Exception {
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        InMemoryGatewayRegistry gateways = gateways();
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom(
                "gateway-b", "10.0.0.9", 18013, "spiffe://gateway/b"));
        for (String gatewayId : List.of("gateway-a", "gateway-b")) {
            connections.register(ConnectionRegistration.active(
                    new ConnectionRouteId(new UserId(42L), new GatewayId(gatewayId)),
                    new RouteRevision(0L), Instant.MAX));
        }
        CountDownLatch bothStarted = new CountDownLatch(2);
        GatewayClient gatewayClient = mock(GatewayClient.class);
        when(gatewayClient.writeFrame(any(), any())).thenAnswer(invocation -> {
            GatewayEndpointDTO gateway = invocation.getArgument(0);
            assertThat(Thread.currentThread().isVirtual()).isTrue();
            bothStarted.countDown();
            if (!bothStarted.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Gateway writes did not run concurrently");
            }
            if ("gateway-b".equals(gateway.gatewayId())) {
                throw new IllegalStateException("gateway unavailable");
            }
            return new GatewayFrameWriteResult(List.of("connection-a"), List.of());
        });

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            FrameDispatchAppService service = service(connections, gateways,
                    lookupService(new AtomicInteger(), List.of()), gatewayClient, executor);

            BrokerFrameWriteResult result = service.dispatch(new FrameDispatchCommand(
                    42L, frame(), "delivery-concurrent", 7L));

            assertThat(bothStarted.getCount()).isZero();
            assertThat(result.acceptedConnectionIds()).containsExactly("connection-a");
        }
    }

    @Test
    void lookupRoutesAreRevalidatedAndSameGatewayIsWrittenOnce() {
        AtomicInteger lookups = new AtomicInteger();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        InMemoryGatewayRegistry gateways = gateways();
        List<RouteLocation> routes = List.of(
                new RouteLocation("gateway-a", "10.0.0.8", 18012, 8L, 7L),
                new RouteLocation("gateway-a", "10.0.0.8", 18012, 9L, 7L));
        RecordingGatewayClient gatewayClient = new RecordingGatewayClient();
        FrameDispatchAppService service = service(connections, gateways,
                lookupService(lookups, routes), gatewayClient);

        BrokerFrameWriteResult result = service.dispatch(new FrameDispatchCommand(
                42L, frame(), "delivery-2", 7L));

        assertThat(lookups).hasValue(1);
        assertThat(gatewayClient.requests).hasSize(1);
        assertThat(gatewayClient.requests.getFirst().deliveryId()).isEqualTo("delivery-2");
        assertThat(gatewayClient.requests.getFirst().deliveryAttemptId()).isNotBlank();
        assertThat(result.acceptedConnectionIds()).containsExactly("connection-a");
    }

    @Test
    void returnsRouteUnavailableWhenLookupAlsoMisses() {
        AtomicInteger lookups = new AtomicInteger();
        FrameDispatchAppService service = service(
                new InMemoryConnectionRegistry(), gateways(),
                lookupService(lookups, List.of()), new RecordingGatewayClient());

        BrokerFrameWriteResult result = service.dispatch(new FrameDispatchCommand(
                42L, frame(), "delivery-3", 7L));

        assertThat(result.processed()).isFalse();
        assertThat(result.code()).isEqualTo("ROUTE_UNAVAILABLE");
        assertThat(lookups).hasValue(1);
    }

    private FrameDispatchAppService service(InMemoryConnectionRegistry connections,
                                         InMemoryGatewayRegistry gateways,
                                         RouteLookupStub routeLookupPeerClient,
                                         GatewayClient gatewayClient) {
        return service(connections, gateways, routeLookupPeerClient, gatewayClient, Runnable::run);
    }

    private FrameDispatchAppService service(InMemoryConnectionRegistry connections,
                                         InMemoryGatewayRegistry gateways,
                                         RouteLookupStub routeLookupPeerClient,
                                         GatewayClient gatewayClient,
                                         Executor frameDispatchExecutor) {
        BrokerClusterConsensus repository = mock(BrokerClusterConsensus.class);
        when(repository.committedState()).thenReturn(new BrokerClusterState(1L, 7L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-current", "127.0.0.1:18020",
                                BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                        new BrokerNode("broker-old", "127.0.0.1:18021",
                                BrokerMembershipRole.VOTER, BrokerRoutingState.DRAINING))));
        BrokerClusterAdapter adapter = routeLookupPeerClient.adapter(gatewayClient, frameDispatchExecutor);
        org.mockito.Mockito.when(adapter.committedState()).thenAnswer(ignored -> repository.committedState());
        org.mockito.Mockito.when(adapter.localBrokerId()).thenReturn(new BrokerId("broker-current"));
        return new FrameDispatchAppService(connections, gateways, adapter,
                new RegistryProperties().getGatewayTtl(), Duration.ofMillis(50),
                RouteLookupStub.negativeCache(Duration.ofMillis(50)));
    }

    private RouteLookupStub lookupService(AtomicInteger lookups,
                                                List<RouteLocation> routes) {
        return (ignored, targetUserId, routingEpoch) -> {
            lookups.incrementAndGet();
            return CompletableFuture.completedFuture(routes);
        };
    }

    private InMemoryGatewayRegistry gateways() {
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-a", "10.0.0.8", 18012, "spiffe://gateway/a"));
        return gateways;
    }

    private FrameResponse frame() {
        return new FrameResponse("1", FrameType.PUSH, "message.private.sent",
                "1", "trace", null, null, Map.of());
    }

    private static final class RecordingGatewayClient extends GatewayClient {
        private final List<GatewayFrameWriteParams> requests = new ArrayList<>();

        private RecordingGatewayClient() {
            super(unusedInvoker(), 100);
        }

        @Override
        public GatewayFrameWriteResult writeFrame(
                GatewayEndpointDTO gateway,
                GatewayFrameWriteParams request) {
            requests.add(request);
            return new GatewayFrameWriteResult(List.of("connection-a"), List.of());
        }

        private static BoltInvoker unusedInvoker() {
            return new BoltInvoker() {
                @Override
                public <T, R> R invoke(String address, String service, String operation,
                                       T request, Class<R> responseType, int timeoutMillis) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
