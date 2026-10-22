package com.co.kc.imchat.gateway.ws.routing;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotAckParams;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.broker.sdk.model.result.AcceptGatewayRoutesResult;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelectors;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayRouteSnapshotCoordinatorTest {

    @Test
    void groupsUsersByRouteBrokerAndChunksOnlyDirectedShards() {
        BrokerClusterState state = state(3L, "broker-a", "broker-b", "broker-c");
        ConnectionRegistry registry = registryWithOneUserPerBroker(state);
        CapturingClient client = new CapturingClient(state);

        try (GatewayRouteSnapshotCoordinator coordinator = coordinator(registry, client, 1, 2, ignored -> { })) {
            coordinator.synchronize();
        }

        assertThat(client.requests).hasSize(3);
        assertThat(client.requests).allSatisfy(request -> {
            assertThat(request.userIds()).hasSize(1);
            String selected = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                    .select(request.userIds().get(0), state).brokerId();
            assertThat(request.routeBrokerId()).isEqualTo(selected);
            assertThat(request.chunkCount()).isEqualTo(1);
        });
        assertThat(client.acks).singleElement().satisfies(ack -> {
            assertThat(ack.gatewayId()).isEqualTo("gateway-process-1");
            assertThat(ack.routingEpoch()).isEqualTo(3L);
            assertThat(ack.routeCount()).isEqualTo(3L);
            assertThat(ack.routeRevision()).isPositive();
            assertThat(ack.contentChecksum()).isNotBlank();
            assertThat(ack.updateExpireAtMs()).isEqualTo(6_000L);
        });
    }

    @Test
    void sendsOneEmptyShardOnlyToBrokerThatHeldPreviousUsers() {
        BrokerClusterState state = state(3L, "broker-a", "broker-b", "broker-c");
        ConnectionRegistry registry = new ConnectionRegistry();
        long userId = userFor("broker-b", state);
        registry.register(userId, "session", "connection", new EmbeddedChannel());
        CapturingClient client = new CapturingClient(state);

        try (GatewayRouteSnapshotCoordinator coordinator = coordinator(registry, client, 10, 2, ignored -> { })) {
            coordinator.synchronize();
            client.requests.clear();
            registry.unregister("connection");
            coordinator.synchronize();
        }

        assertThat(client.requests).singleElement().satisfies(request -> {
            assertThat(request.routeBrokerId()).isEqualTo("broker-b");
            assertThat(request.userIds()).isEmpty();
            assertThat(request.chunkCount()).isEqualTo(1);
        });
    }

    @Test
    void refreshesClusterStateAndResendsAtNewEpochAfterRouteMoved() {
        BrokerClusterState first = state(3L, "broker-a", "broker-b");
        BrokerClusterState second = state(4L, "broker-b", "broker-c");
        ConnectionRegistry registry = new ConnectionRegistry();
        registry.register(101L, "session", "connection", new EmbeddedChannel());
        CapturingClient client = new CapturingClient(first);
        client.refreshedState = second;
        client.moveFirstRequest = true;

        try (GatewayRouteSnapshotCoordinator coordinator = coordinator(registry, client, 10, 2, ignored -> { })) {
            coordinator.synchronize();
        }

        assertThat(client.refreshCount).isEqualTo(1);
        assertThat(client.requests).extracting(GatewayRouteSnapshotParams::routingEpoch)
                .contains(3L, 4L);
    }

    @Test
    void appliesDeterministicJitterAndHonorsBrokerConcurrencyLimit() {
        BrokerClusterState state = state(3L, "broker-a", "broker-b", "broker-c");
        ConnectionRegistry registry = registryWithOneUserPerBroker(state);
        CapturingClient client = new CapturingClient(state);
        client.releaseAtConcurrentSends = 2;
        List<Duration> delays = java.util.Collections.synchronizedList(new ArrayList<>());

        try (GatewayRouteSnapshotCoordinator coordinator = coordinator(
                registry, client, 10, 2, delays::add)) {
            coordinator.synchronize();
        }

        assertThat(client.maxConcurrentSends.get()).isEqualTo(2);
        assertThat(delays).hasSize(3).allMatch(duration -> !duration.isNegative()
                && duration.toMillis() <= 20L);
    }

    @Test
    void retriesBackpressureWithBoundedJitteredExponentialDelay() {
        BrokerClusterState state = state(3L, "broker-a");
        ConnectionRegistry registry = registryWithOneUserPerBroker(state);
        CapturingClient client = new CapturingClient(state);
        client.retryFirstRequest = true;
        List<Duration> delays = new ArrayList<>();

        try (GatewayRouteSnapshotCoordinator coordinator = coordinator(
                registry, client, 10, 1, delays::add)) {
            coordinator.synchronize();
        }

        assertThat(client.requests).hasSize(2);
        assertThat(delays).hasSize(2);
        long jitterMs = Math.floorMod(java.util.Objects.hash(
                "gateway-process-1", 3L, "broker-a", 0), 51L);
        assertThat(delays.get(1)).isEqualTo(Duration.ofMillis(125L + jitterMs));
        assertThat(client.requests.get(0).updateExpireAtMs()).isEqualTo(6_000L);
        assertThat(client.requests.get(1)).isSameAs(client.requests.get(0));
    }

    @Test
    void stopsWhenBackoffWouldReachOriginalExpiry() {
        BrokerClusterState state = state(3L, "broker-a");
        CapturingClient client = new CapturingClient(state);
        client.retryFirstRequest = true;
        List<Duration> delays = new ArrayList<>();
        long jitterMs = Math.floorMod(java.util.Objects.hash(
                "gateway-process-1", 3L, "broker-a", 0), 51L);

        try (GatewayRouteSnapshotCoordinator coordinator = new GatewayRouteSnapshotCoordinator(
                "gateway-process-1", registryWithOneUserPerBroker(state), client,
                10, 1, 125L + jitterMs, 0L, () -> 1_000L, delays::add)) {
            assertThatThrownBy(coordinator::synchronize)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("backpressure retry exhausted");
        }

        assertThat(client.requests).singleElement().satisfies(request ->
                assertThat(request.updateExpireAtMs()).isEqualTo(1_125L + jitterMs));
        assertThat(client.acks).isEmpty();
        assertThat(delays).isEmpty();
    }

    private GatewayRouteSnapshotCoordinator coordinator(
            ConnectionRegistry registry,
            CapturingClient client,
            int chunkSize,
            int maxConcurrent,
            GatewayRouteSnapshotCoordinator.Delay delay) {
        return new GatewayRouteSnapshotCoordinator("gateway-process-1", registry, client,
                chunkSize, maxConcurrent, 5_000L, 20L, () -> 1_000L, delay);
    }

    private ConnectionRegistry registryWithOneUserPerBroker(BrokerClusterState state) {
        ConnectionRegistry registry = new ConnectionRegistry();
        for (BrokerNode broker : state.activeBrokers()) {
            long userId = userFor(broker.brokerId(), state);
            registry.register(userId, "session-" + broker.brokerId(),
                    "connection-" + broker.brokerId(), new EmbeddedChannel());
        }
        return registry;
    }

    private long userFor(String brokerId, BrokerClusterState state) {
        for (long userId = 1L; userId < 100_000L; userId++) {
            if (RouteBrokerSelectors.forAlgorithm(state.algorithm())
                    .select(userId, state).brokerId().equals(brokerId)) {
                return userId;
            }
        }
        throw new IllegalStateException("no user found for " + brokerId);
    }

    private BrokerClusterState state(long epoch, String... brokerIds) {
        List<BrokerNode> brokers = java.util.Arrays.stream(brokerIds)
                .map(id -> new BrokerNode(id, id + ":18020",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE))
                .toList();
        return new BrokerClusterState(epoch, epoch,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, brokers);
    }

    private static final class CapturingClient
            implements GatewayRouteSnapshotCoordinator.SnapshotClient {
        private volatile BrokerClusterState state;
        private BrokerClusterState refreshedState;
        private final List<GatewayRouteSnapshotParams> requests =
                java.util.Collections.synchronizedList(new ArrayList<>());
        private final List<GatewayRouteSnapshotAckParams> acks =
                java.util.Collections.synchronizedList(new ArrayList<>());
        private final Map<String, AtomicInteger> brokerCalls = new ConcurrentHashMap<>();
        private final AtomicInteger concurrentSends = new AtomicInteger();
        private final AtomicInteger maxConcurrentSends = new AtomicInteger();
        private final CountDownLatch concurrentSendGate = new CountDownLatch(1);
        private int refreshCount;
        private boolean moveFirstRequest;
        private boolean retryFirstRequest;
        private int releaseAtConcurrentSends;

        private CapturingClient(BrokerClusterState state) {
            this.state = state;
        }

        @Override
        public BrokerClusterState currentState() {
            return state;
        }

        @Override
        public void refreshState() {
            refreshCount++;
            if (refreshedState != null) {
                state = refreshedState;
            }
        }

        @Override
        public AcceptGatewayRoutesResult send(GatewayRouteSnapshotParams params) {
            requests.add(params);
            int active = concurrentSends.incrementAndGet();
            maxConcurrentSends.accumulateAndGet(active, Math::max);
            try {
                if (releaseAtConcurrentSends > 0) {
                    if (active >= releaseAtConcurrentSends) {
                        concurrentSendGate.countDown();
                    }
                    concurrentSendGate.await();
                }
                int call = brokerCalls.computeIfAbsent(
                        params.routeBrokerId(), ignored -> new AtomicInteger()).incrementAndGet();
                if (moveFirstRequest && requests.size() == 1 && call == 1) {
                    return new AcceptGatewayRoutesResult(
                            AcceptGatewayRoutesResult.Status.ROUTE_MOVED,
                            params.routingEpoch() + 1L, 0L);
                }
                if (retryFirstRequest && requests.size() == 1 && call == 1) {
                    return new AcceptGatewayRoutesResult(
                            AcceptGatewayRoutesResult.Status.RETRY_AFTER,
                            params.routingEpoch(), 125L);
                }
                return AcceptGatewayRoutesResult.applied(params.routingEpoch());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                concurrentSends.decrementAndGet();
            }
        }

        @Override
        public GatewayRouteSnapshotAckResult acknowledge(GatewayRouteSnapshotAckParams params) {
            acks.add(params);
            return GatewayRouteSnapshotAckResult.notRequired();
        }
    }
}
