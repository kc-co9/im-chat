package com.co.kc.imchat.broker.application.local;

import com.co.kc.imchat.broker.domain.connection.model.UserId;

import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryBatchConnectionRegistry;
import com.co.kc.imchat.broker.support.RouteCacheStub;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import static com.co.kc.imchat.broker.support.BrokerRoutingPropertiesFixture.routingProperties;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.repository.FenceWatermarkRepository;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptGatewayRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CleanupDrainRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.dto.GatewayRouteWatermark;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.sdk.model.result.AcceptGatewayRoutesResult;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotParams;
import com.co.kc.imchat.broker.interfaces.handler.connection.GatewayRouteSnapshotHandler;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.broker.sdk.routing.GatewayRouteSnapshotChecksum;
import com.co.kc.imchat.broker.support.observation.NoopDomainEventPublisher;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionSyncedEvent;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import com.co.kc.imchat.broker.support.TestClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;

class LocalRouteAppServiceReceiveTest {
    private LocalRouteAppService createService(
            String brokerId, BrokerClusterConsensus consensus, LocalRoutingLeaseService lease,
            ConnectionRegistry connections, DomainEventPublisher publisher, BrokerRoutingProperties properties) {
        return new LocalRouteAppService(brokerId, consensus, lease,
                new InMemoryBatchConnectionRegistry(connections, properties,
                        RouteCacheStub.local(properties.getSnapshotBatchTimeout(), properties.getMaxPendingSnapshotBatches()),
                        RouteCacheStub.local(Duration.ofDays(3650), properties.getDrainCleanupResultCapacity())),
                publisher, properties.getSnapshotBatchTimeout());
    }

    private static final String BROKER_ID = "broker-a";
    private static final String GATEWAY_ID = "gateway-process-1";

    private final AtomicLong clock = new AtomicLong(1_000L);
    private final InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
    private LocalRouteAppService service;
    private MockedStatic<Instant> wallClock;

    @BeforeEach
    void setUp() {
        wallClock = TestClock.interceptNow(clock::get);
        service = createService(BROKER_ID,
                new FixedRepository(state()), authorizedLeaseService(), connections,
                new NoopDomainEventPublisher(),
                routingProperties(
                        2, 1_024L, java.time.Duration.ofMillis(100), 10));
    }

    @AfterEach
    void releaseWallClock() {
        wallClock.close();
    }

    @Test
    void receivesRouteChunkWhileCleanupWaitsForCommittedOperation() throws Exception {
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        BrokerClusterOperation operation = mock(BrokerClusterOperation.class);
        when(operation.matchesRouteCleanup(any(), any(), any(), any(), any())).thenReturn(true);
        BrokerClusterConsensus consensus = mock(BrokerClusterConsensus.class);
        when(consensus.committedState()).thenReturn(state());
        when(consensus.findOperation(new ClusterOperationId("drain-lock"))).thenAnswer(invocation -> {
            cleanupEntered.countDown();
            if (!releaseCleanup.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("cleanup release timed out");
            }
            return Optional.of(operation);
        });
        service = createService(BROKER_ID, consensus, authorizedLeaseService(), connections,
                new NoopDomainEventPublisher(),
                routingProperties(2, 1024L, Duration.ofSeconds(1), 10));
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<DrainRouteCleanupResult> cleanup = executor.submit(() -> service.cleanupRoutes(new CleanupDrainRoutesCommand(
                    "broker-leader", "drain-lock", 6L, 7L, Long.MAX_VALUE,
                    List.of(new GatewayRouteWatermark(GATEWAY_ID, 1L, 0L, GatewayRouteSnapshotChecksum.calculate(List.of()))))));
            try {
                assertThat(cleanupEntered.await(5, TimeUnit.SECONDS)).isTrue();
                Future<AcceptGatewayRoutesResult> received = executor.submit(() -> service.acceptRoutes(
                        chunk("independent", 0, 2, GatewayRouteSnapshotChecksum.calculate(List.of(101L, 102L)), List.of(101L))));
                assertThat(received.get(2, TimeUnit.SECONDS).status())
                        .isEqualTo(AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
            } finally {
                releaseCleanup.countDown();
            }
            assertThat(cleanup.get(5, TimeUnit.SECONDS).removedOldRouteCount()).isZero();
        }
    }

    @Test
    void handlerPropagatesLeaseExecutionFailureWithoutWrapping() throws Exception {
        LocalRoutingLeaseService leaseService = mock(LocalRoutingLeaseService.class);
        Exception failure = new Exception("lease execution failed");
        when(leaseService.executeOrElse(any(), any(), any())).thenThrow(failure);
        service = createService(BROKER_ID, new FixedRepository(state()), leaseService, connections,
                new NoopDomainEventPublisher(), routingProperties(2, 1024L, Duration.ofSeconds(1), 10));
        GatewayRouteSnapshotHandler handler = new GatewayRouteSnapshotHandler(
                service);
        GatewayRouteSnapshotParams request = new GatewayRouteSnapshotParams(GATEWAY_ID, 7L, 3L, BROKER_ID,
                "failed-batch", 0, 1, GatewayRouteSnapshotChecksum.calculate(List.of(101L)), Long.MAX_VALUE, List.of(101L));

        assertThatThrownBy(() -> BrokerRequestChainTestSupport.passThroughChain(handler).handle(null, JsonUtils.toJson(request))).isSameAs(failure);
        assertThat(connections.find(new UserId(101L))).isEmpty();
    }

    @Test
    void completeBatchDoesNotWriteRegistryWhenLeaseIsUnavailable() throws Exception {
        LocalRoutingLeaseService leaseService = mock(LocalRoutingLeaseService.class);
        when(leaseService.executeOrElse(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        List<DomainEvent> events = new ArrayList<>();
        DomainEventPublisher publisher = new DomainEventPublisher() {
            public void publish(DomainEvent event) { events.add(event); }
            public void publish(List<DomainEvent> published) { events.addAll(published); }
        };
        service = createService(BROKER_ID, new FixedRepository(state()), leaseService, connections,
                publisher, routingProperties(1, 1024L, Duration.ofSeconds(1), 10));

        AcceptGatewayRoutesResult result = service.acceptRoutes(chunk("fenced", 0, 1,
                GatewayRouteSnapshotChecksum.calculate(List.of(101L)), List.of(101L)));

        assertThat(result.status()).isEqualTo(AcceptGatewayRoutesResult.Status.RETRY_AFTER);
        assertThat(connections.list()).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void staleSnapshotPublishesCurrentRoutesInsteadOfStaleInput() throws Exception {
        List<DomainEvent> events = new ArrayList<>();
        service = createService(BROKER_ID, new FixedRepository(state()),
                authorizedLeaseService(), connections, new DomainEventPublisher() {
                    public void publish(DomainEvent event) { events.add(event); }
                    public void publish(List<DomainEvent> published) { events.addAll(published); }
                },
                routingProperties(2, 1024L, Duration.ofMillis(100), 10));
        service.acceptRoutes(chunk("new", 5L, 0, 1, GatewayRouteSnapshotChecksum.calculate(List.of(102L)), List.of(102L)));
        events.clear();

        service.acceptRoutes(chunk("old", 3L, 0, 1, GatewayRouteSnapshotChecksum.calculate(List.of(101L)), List.of(101L)));

        assertThat(events).filteredOn(ConnectionSyncedEvent.class::isInstance)
                .singleElement().satisfies(event -> assertThat(
                        ((ConnectionSyncedEvent) event).userIds()).containsExactly(102L));
        assertThat(connections.find(new UserId(101L))).isEmpty();
        assertThat(connections.find(new UserId(102L))).hasSize(1);
    }

    @Test
    void appliesOnlyAfterEveryChunkArrivesAndChecksumMatches() throws Exception {
        List<Long> users = List.of(101L, 102L);
        String checksum = GatewayRouteSnapshotChecksum.calculate(users);

        AcceptGatewayRoutesResult first = service.acceptRoutes(
                chunk("batch-1", 0, 2, checksum, List.of(101L)));

        assertThat(first.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
        assertThat(connections.list()).isEmpty();

        AcceptGatewayRoutesResult second = service.acceptRoutes(
                chunk("batch-1", 1, 2, checksum, List.of(102L)));

        assertThat(second.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.ROUTES_APPLIED);
        assertThat(connections.list()).extracting(route -> route.userId().value())
                .containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    void rejectsCompleteBatchWithWrongChecksumWithoutApplyingPartialState() throws Exception {
        AcceptGatewayRoutesResult first = service.acceptRoutes(
                chunk("batch-1", 0, 2, GatewayRouteSnapshotChecksum.calculate(List.of(999L)), List.of(101L)));
        AcceptGatewayRoutesResult second = service.acceptRoutes(
                chunk("batch-1", 1, 2, GatewayRouteSnapshotChecksum.calculate(List.of(999L)), List.of(102L)));

        assertThat(first.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
        assertThat(second.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.INVALID_BATCH);
        assertThat(connections.list()).isEmpty();
    }

    @Test
    void expiresIncompleteBatchAndDoesNotCombineItWithLateChunk() throws Exception {
        List<Long> users = List.of(101L, 102L);
        String checksum = GatewayRouteSnapshotChecksum.calculate(users);
        service.acceptRoutes(chunk("batch-1", 0, 2, checksum, List.of(101L)));

        clock.set(1_101L);
        AcceptGatewayRoutesResult late = service.acceptRoutes(
                chunk("batch-1", 1, 2, checksum, List.of(102L)));

        assertThat(late.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
        assertThat(connections.list()).isEmpty();
    }

    @Test
    void repeatedChunkDoesNotExtendBatchCacheExpiration() throws Exception {
        String checksum = GatewayRouteSnapshotChecksum.calculate(List.of(101L, 102L));
        AcceptGatewayRoutesCommand first = chunk("batch-1", 0, 2, checksum, List.of(101L));
        assertThat(service.acceptRoutes(first).status()).isEqualTo(AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);

        clock.set(1_050L);
        assertThat(service.acceptRoutes(first).status()).isEqualTo(AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
        clock.set(1_101L);
        assertThat(service.acceptRoutes(chunk("batch-1", 1, 2, checksum, List.of(102L))).status())
                .isEqualTo(AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
        assertThat(connections.list()).isEmpty();
    }

    @Test
    void returnsBackpressureWhenPendingBatchLimitIsReached() throws Exception {
        String checksum = GatewayRouteSnapshotChecksum.calculate(List.of(101L, 102L));
        service = serviceWithPendingLimit(1);
        service.acceptRoutes(chunk("batch-1", 0, 2, checksum, List.of(101L)));

        AcceptGatewayRoutesResult rejected = service.acceptRoutes(
                chunk("batch-2", 0, 2, checksum, List.of(101L)));

        assertThat(rejected.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.RETRY_AFTER);
        assertThat(rejected.retryAfterMillis()).isEqualTo(100L);
    }

    @Test
    void retainsEarlierChunksWhenLaterChunkIsBackpressured() throws Exception {
        List<Long> targetUsers = List.of(101L, 102L);
        String targetChecksum = GatewayRouteSnapshotChecksum.calculate(targetUsers);
        List<Long> competingUsers = List.of(201L, 202L);
        String competingChecksum = GatewayRouteSnapshotChecksum.calculate(competingUsers);
        service = serviceWithPendingLimits(2, 24L);

        service.acceptRoutes(
                chunk("target", 4L, 0, 2, targetChecksum, List.of(101L)));
        service.acceptRoutes(
                chunk("competing", 3L, 0, 2, competingChecksum, competingUsers));

        AcceptGatewayRoutesResult backpressured = service.acceptRoutes(
                chunk("target", 4L, 1, 2, targetChecksum, List.of(102L)));
        AcceptGatewayRoutesResult competingApplied = service.acceptRoutes(
                chunk("competing", 3L, 1, 2, competingChecksum, List.of()));
        AcceptGatewayRoutesResult targetApplied = service.acceptRoutes(
                chunk("target", 4L, 1, 2, targetChecksum, List.of(102L)));

        assertThat(backpressured.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.RETRY_AFTER);
        assertThat(competingApplied.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.ROUTES_APPLIED);
        assertThat(targetApplied.status()).isEqualTo(
                AcceptGatewayRoutesResult.Status.ROUTES_APPLIED);
        assertThat(connections.list()).extracting(route -> route.userId().value())
                .containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    void repeatedChunkDoesNotConsumeByteCapacityTwice() throws Exception {
        service = serviceWithPendingLimits(1, 2L * Long.BYTES);
        String checksum = GatewayRouteSnapshotChecksum.calculate(List.of(101L, 102L));
        AcceptGatewayRoutesCommand first = chunk("duplicate", 0, 2, checksum, List.of(101L));

        assertThat(service.acceptRoutes(first).status()).isEqualTo(AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
        assertThat(service.acceptRoutes(first).status()).isEqualTo(AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
        assertThat(service.acceptRoutes(chunk("duplicate", 1, 2, checksum, List.of(102L))).status())
                .isEqualTo(AcceptGatewayRoutesResult.Status.ROUTES_APPLIED);
        assertThat(connections.list()).extracting(route -> route.userId().value()).containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    void conflictingRepeatedChunkDiscardsEarlierBatch() throws Exception {
        String checksum = GatewayRouteSnapshotChecksum.calculate(List.of(101L, 102L));
        service.acceptRoutes(chunk("conflict", 0, 2, checksum, List.of(101L)));

        assertThat(service.acceptRoutes(chunk("conflict", 0, 2, checksum, List.of(102L))).status())
                .isEqualTo(AcceptGatewayRoutesResult.Status.INVALID_BATCH);
        assertThat(service.acceptRoutes(chunk("conflict", 1, 2, checksum, List.of(102L))).status())
                .isEqualTo(AcceptGatewayRoutesResult.Status.CHUNK_ACCEPTED);
        assertThat(connections.list()).isEmpty();
        assertThat(service.acceptRoutes(chunk("conflict", 0, 2, checksum, List.of(101L))).status())
                .isEqualTo(AcceptGatewayRoutesResult.Status.ROUTES_APPLIED);
    }

    @Test
    void malformedDigestPropagatesBeforeBatchIsStored() throws Exception {
        service = serviceWithPendingLimit(1);
        assertThatThrownBy(() -> service.acceptRoutes(chunk("malformed", 0, 1, "wrong", List.of(101L))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("lowercase SHA-256");
        assertThat(service.acceptRoutes(chunk("valid", 0, 1,
                GatewayRouteSnapshotChecksum.calculate(List.of(101L)), List.of(101L))).status())
                .isEqualTo(AcceptGatewayRoutesResult.Status.ROUTES_APPLIED);
    }

    private LocalRouteAppService serviceWithPendingLimit(int limit) {
        return serviceWithPendingLimits(limit, 1_024L);
    }

    private LocalRouteAppService serviceWithPendingLimits(int limit, long maxBytes) {
        return createService(BROKER_ID,
                new FixedRepository(state()), authorizedLeaseService(), connections,
                new NoopDomainEventPublisher(),
                routingProperties(
                        limit, maxBytes, java.time.Duration.ofMillis(100), 10));
    }

    private AcceptGatewayRoutesCommand chunk(String batchId, int index, int count,
                                             String checksum, List<Long> users) {
        return chunk(batchId, 3L, index, count, checksum, users);
    }

    private AcceptGatewayRoutesCommand chunk(String batchId, long routeRevision,
                                             int index, int count,
                                             String checksum, List<Long> users) {
        return new AcceptGatewayRoutesCommand(GATEWAY_ID, 7L, routeRevision, BROKER_ID,
                batchId, index, count, checksum, Long.MAX_VALUE, users);
    }

    private BrokerClusterState state() {
        return new BrokerClusterState(7L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode(BROKER_ID, "127.0.0.1:18020",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
    }

    private LocalRoutingLeaseService authorizedLeaseService() {
        LocalRoutingLeaseService service = new LocalRoutingLeaseService(
                new BrokerId(BROKER_ID),
                () -> new RoutingEpoch(7L), emptyWatermarkRepository());
        RoutingLease lease = RoutingLease.builder()
                .brokerId(new BrokerId(BROKER_ID))
                .routingEpoch(new RoutingEpoch(7L))
                .leaderTerm(term(1L))
                .leaseSequence(leaseSequence(1L))
                .leaseNonce(new LeaseNonce("nonce-1"))
                .leaseDuration(Duration.ofSeconds(60L))
                .quorumReadIndex(readIndex(1L))
                .build();
        service.acceptLease(lease, new PreparedLeaseRequest(new LeaseNonce("nonce-1"),
                new RoutingEpoch(7L), MonotonicTimestamp.now()));
        return service;
    }

    private FenceWatermarkRepository emptyWatermarkRepository() {
        return new FenceWatermarkRepository() {
            @Override
            public Optional<RoutingEpoch> highestFencedEpoch() {
                return Optional.empty();
            }

            @Override
            public void advanceTo(RoutingEpoch routingEpoch) {
            }
        };
    }

    private record FixedRepository(BrokerClusterState committedState)
            implements BrokerClusterConsensusStub {
        @Override
        public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
            return Optional.empty();
        }

        @Override
        public CompletionStage<BrokerClusterConsensusResult> submitChange(BrokerClusterConsensusCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
