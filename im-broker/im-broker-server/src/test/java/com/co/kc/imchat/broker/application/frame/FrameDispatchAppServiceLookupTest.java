package com.co.kc.imchat.broker.application.frame;

import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;

import com.co.kc.imchat.broker.domain.connection.model.GatewayHost;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPort;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPrincipalId;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.gateway.GatewayDispatchAdapter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;

import com.co.kc.imchat.broker.support.RouteLookupStub;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import com.co.kc.imchat.broker.support.TestClock;
import java.time.Instant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FrameDispatchAppServiceLookupTest {

    private final AtomicLong clock = new AtomicLong(1_000L);
    private MockedStatic<Instant> wallClock;

    @BeforeEach
    void controlWallClock() {
        wallClock = TestClock.interceptNow(clock::get);
    }

    @AfterEach
    void releaseWallClock() {
        wallClock.close();
    }

    @Test
    void queriesAllCurrentCandidatesInOneWave() {
        RecordingPeerClient peers = new RecordingPeerClient(Map.of(
                "broker-old", List.of(route("gateway-a", 9L)),
                "broker-extra", List.of(route("gateway-b", 8L))));
        FrameDispatchAppService service = service(peers,
                List.of(candidate("broker-old")),
                List.of(candidate("broker-old"), candidate("broker-extra")));

        BrokerFrameWriteResult result = service.dispatch(command("lookup-1"));

        assertThat(result.acceptedConnectionIds()).containsExactlyInAnyOrder(
                "connection-gateway-a", "connection-gateway-b");
        assertThat(peers.queriedBrokerIds()).containsExactlyInAnyOrder("broker-old", "broker-extra");
    }

    @Test
    void singleWaveRunsInParallelAndMergesGateways() {
        RecordingPeerClient peers = new RecordingPeerClient(Map.of(
                "broker-old", List.of(),
                "broker-a", List.of(new RouteLocation("gateway-a", "127.0.0.2", 18012, 7L, 7L)),
                "broker-b", List.of(route("gateway-a", 9L), route("gateway-b", 4L))));
        FrameDispatchAppService service = service(peers,
                List.of(candidate("broker-old")),
                List.of(candidate("broker-a"), candidate("broker-b")));

        BrokerFrameWriteResult result = service.dispatch(command("lookup-2"));

        assertThat(result.acceptedConnectionIds()).containsExactlyInAnyOrder(
                "connection-gateway-a", "connection-gateway-b");
        assertThat(peers.queriedBrokerIds()).containsExactlyInAnyOrder(
                "broker-old", "broker-a", "broker-b");
        assertThat(peers.maxConcurrentQueries()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void repeatedMissUsesShortNegativeCache() {
        AtomicInteger queries = new AtomicInteger();
        RouteLookupStub peers = (candidate, targetUserId, routingEpoch) -> {
            queries.incrementAndGet();
            return CompletableFuture.completedFuture(List.of());
        };
        FrameDispatchAppService service = service(peers, List.of(), List.of(candidate("broker-a")));

        assertThat(service.dispatch(command("lookup-1")).acceptedConnectionIds()).isEmpty();
        assertThat(service.dispatch(command("lookup-2")).acceptedConnectionIds()).isEmpty();
        clock.addAndGet(51L);
        assertThat(service.dispatch(command("lookup-3")).acceptedConnectionIds()).isEmpty();

        assertThat(queries.get()).isEqualTo(2);
    }

    @Test
    void rejectedGatewayRouteIsCachedAsAMiss() {
        AtomicInteger queries = new AtomicInteger();
        RouteLookupStub peers = (candidate, targetUserId, routingEpoch) -> {
            queries.incrementAndGet();
            return CompletableFuture.completedFuture(List.of(
                    new RouteLocation("gateway-a", "127.0.0.2", 18012,
                            9L, 7L)));
        };
        FrameDispatchAppService service = service(peers, List.of(), List.of(candidate("broker-a")));

        assertThat(service.dispatch(command("invalid-gateway-1")).acceptedConnectionIds()).isEmpty();
        assertThat(service.dispatch(command("invalid-gateway-2")).acceptedConnectionIds()).isEmpty();
        assertThat(queries).hasValue(1);
    }

    @Test
    void negativeCacheStartsAfterTheLookupFinishes() {
        AtomicInteger queries = new AtomicInteger();
        RouteLookupStub peers = (candidate, targetUserId, routingEpoch) -> {
            queries.incrementAndGet();
            clock.addAndGet(100L);
            return CompletableFuture.completedFuture(List.of());
        };
        FrameDispatchAppService service = service(peers, List.of(), List.of(candidate("broker-a")));

        assertThat(service.dispatch(command("slow-miss")).acceptedConnectionIds()).isEmpty();
        assertThat(service.dispatch(command("cached-miss")).acceptedConnectionIds()).isEmpty();
        assertThat(queries).hasValue(1);
        clock.addAndGet(51L);
        assertThat(service.dispatch(command("expired-miss")).acceptedConnectionIds()).isEmpty();
        assertThat(queries).hasValue(2);
    }

    @Test
    void synchronousPeerFailureDoesNotDiscardAnotherPeersRoutes() {
        List<String> queried = new ArrayList<>();
        RouteLookupStub peers = (candidate, targetUserId, routingEpoch) -> {
            queried.add(candidate.brokerId());
            if ("broker-a".equals(candidate.brokerId())) {
                throw new IllegalStateException("peer submission failed");
            }
            return CompletableFuture.completedFuture(List.of(route("gateway-b", 4L)));
        };
        FrameDispatchAppService service = service(peers, List.of(),
                List.of(candidate("broker-a"), candidate("broker-b")));

        assertThat(service.dispatch(command("partial-failure")).acceptedConnectionIds())
                .containsExactly("connection-gateway-b");
        assertThat(queried).containsExactly("broker-a", "broker-b");
    }

    @Test
    void sharesDomainParametersAcrossTheWave() {
        List<String> queriedBrokerIds = new ArrayList<>();
        FrameDispatchAppService service = service((candidate, targetUserId, routingEpoch) -> {
            queriedBrokerIds.add(candidate.brokerId());
            assertThat(routingEpoch).isEqualTo(new RoutingEpoch(7L));
            assertThat(targetUserId).isEqualTo(new UserId(42L));
            return CompletableFuture.completedFuture(List.of());
        }, List.of(candidate("broker-old")), List.of(candidate("broker-a")));

        service.dispatch(command("one-hop"));

        assertThat(queriedBrokerIds).containsExactlyInAnyOrder("broker-old", "broker-a");
    }

    @Test
    void queriesAllPeersAndKeepsSuccessfulRoutesWhenAnotherPeerTimesOut() {
        List<String> queried = java.util.Collections.synchronizedList(new ArrayList<>());
        RouteLookupStub peers = (candidate, targetUserId, routingEpoch) -> {
            queried.add(candidate.brokerId());
            if ("broker-a".equals(candidate.brokerId())) {
                return CompletableFuture.completedFuture(List.of(route("gateway-a", 9L)));
            }
            return CompletableFuture.failedFuture(
                    new java.util.concurrent.TimeoutException("peer timeout"));
        };
        FrameDispatchAppService service = service(peers, List.of(),
                List.of(candidate("broker-a"), candidate("broker-b"),
                        candidate("broker-c")));

        BrokerFrameWriteResult result = service.dispatch(command("partial-timeout"));

        assertThat(result.acceptedConnectionIds()).containsExactly("connection-gateway-a");
        assertThat(queried).containsExactly("broker-a", "broker-b", "broker-c");
    }

    @Test
    void queriesEveryCurrentBrokerWithoutFanoutTruncation() {
        List<String> queried = new ArrayList<>();
        RouteLookupStub peers = (candidate, targetUserId, routingEpoch) -> {
            queried.add(candidate.brokerId());
            if ("broker-b".equals(candidate.brokerId())) {
                return CompletableFuture.completedFuture(List.of(route("gateway-b", 4L)));
            }
            return CompletableFuture.completedFuture(List.of());
        };
        FrameDispatchAppService service = service(peers,
                List.of(candidate("broker-a"), candidate("broker-b")),
                List.of(candidate("broker-a"), candidate("broker-b")));

        assertThat(service.dispatch(command("all-brokers")).acceptedConnectionIds())
                .containsExactly("connection-gateway-b");
        assertThat(queried).containsExactly("broker-a", "broker-b");
    }

    @Test
    void queriesBrokerBeyondFormerFanoutLimit() {
        List<String> queried = new ArrayList<>();
        RouteLookupStub peers = (candidate, targetUserId, routingEpoch) -> {
            queried.add(candidate.brokerId());
            return CompletableFuture.completedFuture("broker-33".equals(candidate.brokerId())
                    ? List.of(route("gateway-a", 9L)) : List.of());
        };
        List<BrokerNode> candidates = java.util.stream.IntStream.rangeClosed(1, 33)
                .mapToObj(index -> candidate("broker-" + index))
                .toList();
        FrameDispatchAppService service = service(peers, List.of(), candidates);

        assertThat(service.dispatch(command("all-33")).acceptedConnectionIds())
                .containsExactly("connection-gateway-a");
        assertThat(queried).hasSize(33).contains("broker-33");
    }

    @Test
    void dropsFutureEpochDescriptions() {
        RouteLookupStub peers = (candidate, targetUserId, routingEpoch) ->
                CompletableFuture.completedFuture(List.of(
                        route("gateway-current", 9L),
                        new RouteLocation("gateway-future", "127.0.0.1", 18012,
                                11L, 8L)));
        FrameDispatchAppService service = service(peers, List.of(candidate("broker-old")),
                List.of());

        assertThat(service.dispatch(command("freshness")).acceptedConnectionIds())
                .containsExactly("connection-gateway-current");
    }

    private FrameDispatchAppService service(RouteLookupStub peers,
                                            List<BrokerNode> directed,
                                            List<BrokerNode> all) {
        Map<String, BrokerNode> nodes = new LinkedHashMap<>();
        nodes.put("broker-current", node("broker-current", BrokerRoutingState.ACTIVE));
        all.forEach(candidate -> nodes.put(candidate.brokerId(),
                node(candidate.brokerId(), BrokerRoutingState.ACTIVE)));
        directed.forEach(candidate -> nodes.put(candidate.brokerId(),
                node(candidate.brokerId(), BrokerRoutingState.DRAINING)));
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        when(repository.getCommittedState()).thenReturn(new BrokerClusterState(1L, 7L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.copyOf(nodes.values())));
        GatewayClient gatewayClient = mock(GatewayClient.class);
        when(gatewayClient.writeFrame(any(), any())).thenAnswer(invocation -> {
            GatewayEndpointDTO gateway = invocation.getArgument(0);
            return new GatewayFrameWriteResult(List.of("connection-" + gateway.gatewayId()), List.of());
        });
        GatewayRegistry gateways = mock(GatewayRegistry.class);
        when(gateways.find(any(GatewayId.class))).thenAnswer(invocation ->
                Optional.of(new GatewayRegistration(invocation.<GatewayId>getArgument(0), new GatewayHost("127.0.0.1"), new GatewayPort(18012), new GatewayPrincipalId("identity"), Instant.EPOCH, Instant.now())));
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        org.mockito.Mockito.doAnswer(ignored -> repository.getCommittedState()).when(adapter).getCommittedState();
        when(BrokerRaftTestBindings.bindLocalBroker(adapter).getId()).thenReturn(new BrokerId("broker-current"));
        return new FrameDispatchAppService(mock(ConnectionRegistry.class),
                gateways, adapter, peers.peerAdapter(),
                new GatewayDispatchAdapter(gatewayClient, Runnable::run, mock(MetricsCollector.class)),
                Duration.ofSeconds(1), Duration.ofMillis(50),
                RouteLookupStub.negativeCache(Duration.ofMillis(50)));
    }

    private BrokerNode node(String brokerId, BrokerRoutingState state) {
        return new BrokerNode(brokerId, brokerId + ":18020",
                BrokerMembershipRole.VOTER, state);
    }

    private FrameDispatchCommand command(String deliveryId) {
        FrameResponse frame = new FrameResponse("1", FrameType.PUSH, "message.private.sent",
                null, null, null, null, Map.of());
        return new FrameDispatchCommand(42L, frame, deliveryId, 7L);
    }

    private BrokerNode candidate(String brokerId) {
        return node(brokerId, BrokerRoutingState.ACTIVE);
    }

    private RouteLocation route(String gatewayId, long revision) {
        return new RouteLocation(gatewayId, "127.0.0.1", 18012,
                revision, 7L);
    }

    private static final class RecordingPeerClient implements RouteLookupStub {
        private final Map<String, List<RouteLocation>> routes;
        private final List<String> queried = java.util.Collections.synchronizedList(
                new ArrayList<>());
        private final java.util.concurrent.atomic.AtomicInteger concurrent =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger maxConcurrent =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.CountDownLatch fanoutStarted;

        private RecordingPeerClient(Map<String, List<RouteLocation>> routes) {
            this.routes = routes;
            this.fanoutStarted = new java.util.concurrent.CountDownLatch((int) Math.min(2,
                    routes.keySet().stream().filter(id -> !"broker-old".equals(id)).count()));
        }

        @Override
        public CompletionStage<List<RouteLocation>> lookup(
                BrokerNode candidate,
                UserId userId, RoutingEpoch routingEpoch) {
            queried.add(candidate.brokerId());
            int active = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(active, Math::max);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (!"broker-old".equals(candidate.brokerId())) {
                        fanoutStarted.countDown();
                        try {
                            fanoutStarted.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }
                    return routes.getOrDefault(candidate.brokerId(), List.of());
                } finally {
                    concurrent.decrementAndGet();
                }
            });
        }

        private List<String> queriedBrokerIds() {
            return List.copyOf(queried);
        }

        private int maxConcurrentQueries() {
            return maxConcurrent.get();
        }
    }
}
