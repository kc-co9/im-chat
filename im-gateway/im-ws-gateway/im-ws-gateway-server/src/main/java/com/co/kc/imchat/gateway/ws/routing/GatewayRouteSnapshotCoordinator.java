package com.co.kc.imchat.gateway.ws.routing;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotAckParams;
import com.co.kc.imchat.broker.sdk.model.result.AcceptGatewayRoutesResult;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelectors;
import com.co.kc.imchat.broker.sdk.routing.GatewayRouteSnapshotChecksum;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/**
 * 把 Gateway 完整连接路由按 committed BrokerClusterState 分片并有界发送。
 */
public final class GatewayRouteSnapshotCoordinator implements GatewayRouteSnapshotSync, AutoCloseable {
    private static final int DEFAULT_CHUNK_SIZE = 1_000;
    private static final int DEFAULT_MAX_CONCURRENT_SENDS = 4;
    private static final long DEFAULT_EVENT_TTL_MS = 10_000L;
    private static final long DEFAULT_MAX_JITTER_MS = 250L;
    private static final int MAX_BACKPRESSURE_ATTEMPTS = 3;

    private final String gatewayId;
    private final ConnectionRegistry connectionRegistry;
    private final SnapshotClient client;
    private final int chunkSize;
    private final Duration eventTtl;
    private final Duration maxJitter;
    private final LongSupplier clock;
    private final Delay delay;
    private final ExecutorService executor;
    private Set<String> previousNonEmptyBrokerIds = Set.of();

    public GatewayRouteSnapshotCoordinator(String gatewayId,
                                           ConnectionRegistry connectionRegistry,
                                           BrokerClient brokerClient) {
        this(gatewayId, connectionRegistry, new BrokerSnapshotClient(brokerClient),
                DEFAULT_CHUNK_SIZE, DEFAULT_MAX_CONCURRENT_SENDS,
                DEFAULT_EVENT_TTL_MS, DEFAULT_MAX_JITTER_MS,
                System::currentTimeMillis, duration -> Thread.sleep(duration.toMillis()));
    }

    GatewayRouteSnapshotCoordinator(String gatewayId,
                                    ConnectionRegistry connectionRegistry,
                                    SnapshotClient client,
                                    int chunkSize,
                                    int maxConcurrentSends,
                                    long eventTtlMs,
                                    long maxJitterMs,
                                    LongSupplier clock,
                                    Delay delay) {
        if (gatewayId == null || gatewayId.isBlank() || connectionRegistry == null
                || client == null || chunkSize <= 0 || maxConcurrentSends <= 0
                || eventTtlMs <= 0L || maxJitterMs < 0L
                || clock == null || delay == null) {
            throw new IllegalArgumentException("invalid Gateway route snapshot coordinator configuration");
        }
        this.gatewayId = gatewayId;
        this.connectionRegistry = connectionRegistry;
        this.client = client;
        this.chunkSize = chunkSize;
        this.eventTtl = Duration.ofMillis(eventTtlMs);
        this.maxJitter = Duration.ofMillis(maxJitterMs);
        this.clock = clock;
        this.delay = delay;
        this.executor = Executors.newFixedThreadPool(maxConcurrentSends, runnable -> {
            Thread thread = new Thread(runnable, "gateway-route-snapshot");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 同步一个完整水位；同一进程内不允许多个周期并发排队。 */
    @Override
    public synchronized void synchronize() {
        AttemptResult result = sendAttempt(client.currentState());
        if (result.routeMoved()) {
            client.refreshState();
            result = sendAttempt(client.currentState());
        }
        if (result.routeMoved()) {
            throw new IllegalStateException("Gateway route snapshot rejected after epoch refresh");
        }
        previousNonEmptyBrokerIds = result.nonEmptyBrokerIds();
    }

    private AttemptResult sendAttempt(BrokerClusterState state) {
        ConnectionRegistry.RouteSnapshot snapshot = connectionRegistry.routeSnapshot();
        Map<String, List<Long>> usersByBroker = groupUsers(snapshot.userIds(), state);
        Set<String> activeBrokerIds = state.activeBrokers().stream()
                .map(BrokerNode::brokerId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> targets = new HashSet<>(usersByBroker.keySet());
        previousNonEmptyBrokerIds.stream()
                .filter(activeBrokerIds::contains)
                .forEach(targets::add);

        List<CompletableFuture<Boolean>> sends = targets.stream()
                .sorted()
                .map(brokerId -> CompletableFuture.supplyAsync(
                        () -> sendShard(state, snapshot.routeRevision(), brokerId,
                                usersByBroker.getOrDefault(brokerId, List.of())), executor))
                .toList();
        boolean routeMoved = false;
        for (CompletableFuture<Boolean> send : sends) {
            try {
                routeMoved |= send.join();
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Gateway route snapshot send failed", cause);
            }
        }
        if (!routeMoved) {
            GatewayRouteSnapshotAckResult acknowledgement = client.acknowledge(
                    new GatewayRouteSnapshotAckParams(gatewayId, state.epoch(),
                            snapshot.routeRevision(), snapshot.userIds().size(),
                            GatewayRouteSnapshotChecksum.calculate(snapshot.userIds()),
                            Instant.ofEpochMilli(clock.getAsLong()).plus(eventTtl).toEpochMilli()));
            if (acknowledgement.status() == GatewayRouteSnapshotAckResult.Status.REJECTED
                    || acknowledgement.status()
                    == GatewayRouteSnapshotAckResult.Status.NOT_LEADER) {
                throw new IllegalStateException("Gateway route snapshot ACK rejected: "
                        + acknowledgement.status() + ", " + acknowledgement.message());
            }
        }
        return new AttemptResult(routeMoved, Set.copyOf(usersByBroker.keySet()));
    }

    private Map<String, List<Long>> groupUsers(List<Long> userIds, BrokerClusterState state) {
        Map<String, List<Long>> grouped = new HashMap<>();
        for (Long userId : userIds) {
            String brokerId = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                    .select(userId, state).brokerId();
            grouped.computeIfAbsent(brokerId, ignored -> new ArrayList<>()).add(userId);
        }
        grouped.values().forEach(users -> users.sort(Comparator.naturalOrder()));
        return grouped;
    }

    private boolean sendShard(BrokerClusterState state, long routeRevision,
                              String brokerId, List<Long> userIds) {
        pauseBeforeSend(state.epoch(), brokerId);
        List<List<Long>> chunks = chunks(userIds);
        String batchId = UUID.randomUUID().toString();
        String checksum = GatewayRouteSnapshotChecksum.calculate(userIds);
        Instant updateExpireAt = Instant.ofEpochMilli(clock.getAsLong()).plus(eventTtl);
        boolean routeMoved = false;
        for (int index = 0; index < chunks.size(); index++) {
            GatewayRouteSnapshotParams chunk = new GatewayRouteSnapshotParams(
                    gatewayId, state.epoch(), routeRevision, brokerId, batchId,
                    index, chunks.size(), checksum, updateExpireAt.toEpochMilli(), chunks.get(index));
            AcceptGatewayRoutesResult result = sendWithBackpressure(chunk, brokerId);
            routeMoved |= result.status() == AcceptGatewayRoutesResult.Status.ROUTE_MOVED;
            if (result.status() == AcceptGatewayRoutesResult.Status.INVALID_BATCH
                    || result.status() == AcceptGatewayRoutesResult.Status.UNAUTHORIZED) {
                throw new IllegalStateException("Gateway route snapshot rejected: " + result.status());
            }
            if (routeMoved) {
                break;
            }
        }
        return routeMoved;
    }

    private AcceptGatewayRoutesResult sendWithBackpressure(
            GatewayRouteSnapshotParams chunk, String brokerId) {
        Instant updateExpireAt = Instant.ofEpochMilli(chunk.updateExpireAtMs());
        for (int attempt = 0; attempt < MAX_BACKPRESSURE_ATTEMPTS; attempt++) {
            AcceptGatewayRoutesResult result = client.send(chunk);
            if (result.status() != AcceptGatewayRoutesResult.Status.RETRY_AFTER) {
                return result;
            }
            long exponentialDelayMs = 50L << attempt;
            long jitter = Math.floorMod(java.util.Objects.hash(
                    gatewayId, chunk.routingEpoch(), brokerId, attempt), 51L);
            long delayMs = Math.max(result.retryAfterMs(), exponentialDelayMs) + jitter;
            if (!Instant.ofEpochMilli(clock.getAsLong()).plusMillis(delayMs).isBefore(updateExpireAt)) {
                break;
            }
            try {
                delay.pause(Duration.ofMillis(delayMs));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Gateway route snapshot backpressure wait interrupted", exception);
            }
        }
        throw new IllegalStateException("Gateway route snapshot backpressure retry exhausted");
    }

    private void pauseBeforeSend(long epoch, String brokerId) {
        if (maxJitter.isZero()) {
            return;
        }
        long jitter = Math.floorMod(java.util.Objects.hash(gatewayId, epoch, brokerId),
                maxJitter.toMillis() + 1L);
        try {
            delay.pause(Duration.ofMillis(jitter));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gateway route snapshot jitter interrupted", exception);
        }
    }

    private List<List<Long>> chunks(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of(List.of());
        }
        List<List<Long>> chunks = new ArrayList<>();
        for (int from = 0; from < userIds.size(); from += chunkSize) {
            chunks.add(List.copyOf(userIds.subList(from, Math.min(from + chunkSize, userIds.size()))));
        }
        return chunks;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    interface SnapshotClient {
        BrokerClusterState currentState();

        void refreshState();

        AcceptGatewayRoutesResult send(GatewayRouteSnapshotParams params);

        GatewayRouteSnapshotAckResult acknowledge(GatewayRouteSnapshotAckParams params);
    }

    @FunctionalInterface
    interface Delay {
        void pause(Duration duration) throws InterruptedException;
    }

    private record AttemptResult(boolean routeMoved, Set<String> nonEmptyBrokerIds) {
    }

    private record BrokerSnapshotClient(BrokerClient brokerClient) implements SnapshotClient {
        @Override
        public BrokerClusterState currentState() {
            return brokerClient.currentClusterState();
        }

        @Override
        public void refreshState() {
            brokerClient.refreshClusterState();
        }

        @Override
        public AcceptGatewayRoutesResult send(GatewayRouteSnapshotParams params) {
            return brokerClient.applyGatewayRouteSnapshot(params);
        }

        @Override
        public GatewayRouteSnapshotAckResult acknowledge(GatewayRouteSnapshotAckParams params) {
            return brokerClient.acknowledgeGatewayRouteSnapshot(params);
        }
    }
}
