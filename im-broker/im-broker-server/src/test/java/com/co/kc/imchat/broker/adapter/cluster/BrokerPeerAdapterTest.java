package com.co.kc.imchat.broker.adapter.cluster;

import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.LeaseSequence;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.infrastructure.config.BrokerClusterConfiguration;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.model.dto.RouteLocationDTO;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.params.DrainRouteCleanupParams;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import com.co.kc.imchat.broker.sdk.model.result.BrokerProbeResult;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import com.co.kc.imchat.broker.sdk.model.result.QuorumReadIndexResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeaseInstallResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeasePrepareResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteLookupResult;
import com.co.kc.imchat.broker.sdk.model.result.RoutingFenceAckResult;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerPeerAdapterTest {
    @Test
    void authenticatedProbeTrustsSuccessfulResponseWithoutRecheckingEchoFields() {
        ProbeFixture fixture = new ProbeFixture(Runnable::run);
        when(fixture.invoker.invokeAuthenticated(eq("b:18020"), any(), any(), any(),
                eq(BrokerProbeResult.class), eq(500), eq("spiffe://im-chat/broker/b"),
                eq("spiffe://im-chat/broker/")))
                .thenReturn(new BrokerProbeResult("different-cluster", "different-broker", 9L, 8L, "different-nonce"));
        assertThat(fixture.probe()).isEqualTo(BrokerHealthStatus.HEALTHY);
    }

    @ParameterizedTest
    @EnumSource(BoltInvocationException.Failure.class)
    void probeMapsOnlyExplicitTransportFailureToUnhealthy(BoltInvocationException.Failure failure) {
        ProbeFixture fixture = new ProbeFixture(Runnable::run);
        when(fixture.invoker.invokeAuthenticated(eq("b:18020"), any(), any(), any(),
                eq(BrokerProbeResult.class), eq(500), eq("spiffe://im-chat/broker/b"),
                eq("spiffe://im-chat/broker/")))
                .thenThrow(new CompletionException(new CompletionException(
                        new BoltInvocationException(failure, "controlled", null))));
        assertThat(fixture.probe()).isEqualTo(failure == BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE
                ? BrokerHealthStatus.UNHEALTHY : BrokerHealthStatus.UNKNOWN);
    }

    @Test
    void probeAcceptsValidatedResponse() {
        ProbeFixture fixture = new ProbeFixture(Runnable::run);
        when(fixture.invoker.invokeAuthenticated(eq("b:18020"), any(), any(), any(),
                eq(BrokerProbeResult.class), eq(500), eq("spiffe://im-chat/broker/b"),
                eq("spiffe://im-chat/broker/")))
                .thenReturn(new BrokerProbeResult("cluster-a", "b", 1L, 2L, "expected"));
        assertThat(fixture.probe()).isEqualTo(BrokerHealthStatus.HEALTHY);
    }

    @Test
    void probeAndHealthReportUseConfiguredObservationRpcTimeout() {
        ProbeFixture fixture = new ProbeFixture(Runnable::run, Duration.ofMillis(123));
        when(fixture.invoker.invokeAuthenticated(eq("b:18020"), any(), any(), any(),
                eq(BrokerProbeResult.class), eq(123), eq("spiffe://im-chat/broker/b"),
                eq("spiffe://im-chat/broker/")))
                .thenReturn(new BrokerProbeResult("cluster-a", "b", 1L, 2L, "expected"));
        when(fixture.invoker.invokeAuthenticated(eq("b:18020"), any(), any(), any(),
                eq(BrokerHealthReportResult.class), eq(123), eq("spiffe://im-chat/broker/b"),
                eq("spiffe://im-chat/broker/")))
                .thenReturn(new BrokerHealthReportResult("cluster-a", "b", 1L, 2L, "expected", List.of()));

        assertThat(fixture.probe()).isEqualTo(BrokerHealthStatus.HEALTHY);
        assertThat(fixture.peer.fetchHealthReport(fixture.target, new BrokerClusterId("cluster-a"), new ClusterRevision(1L), new RoutingEpoch(2L), Set.of(new BrokerId("b")))
                .toCompletableFuture().join()).isEmpty();
        verify(fixture.invoker).invokeAuthenticated(eq("b:18020"), any(), any(), any(),
                eq(BrokerProbeResult.class), eq(123), eq("spiffe://im-chat/broker/b"),
                eq("spiffe://im-chat/broker/"));
        verify(fixture.invoker).invokeAuthenticated(eq("b:18020"), any(), any(), any(),
                eq(BrokerHealthReportResult.class), eq(123), eq("spiffe://im-chat/broker/b"),
                eq("spiffe://im-chat/broker/"));
    }

    @Test
    void authenticatedProbeAndReportAcceptRequestsWithoutWallClockDeadline() {
        ProbeFixture fixture = new ProbeFixture(Runnable::run, Duration.ofMillis(500));
        when(fixture.invoker.invokeAuthenticated(any(), any(), any(), any(),
                eq(BrokerProbeResult.class), eq(500), any(), any()))
                .thenReturn(new BrokerProbeResult("cluster-a", "b", 1L, 2L, "expected"));
        when(fixture.invoker.invokeAuthenticated(any(), any(), any(), any(),
                eq(BrokerHealthReportResult.class), eq(500), any(), any()))
                .thenReturn(new BrokerHealthReportResult("cluster-a", "b", 1L, 2L, "expected", List.of()));

        assertThat(fixture.probe()).isEqualTo(BrokerHealthStatus.HEALTHY);
        assertThat(fixture.peer.fetchHealthReport(fixture.target, new BrokerClusterId("cluster-a"), new ClusterRevision(1L), new RoutingEpoch(2L),
                Set.of(new BrokerId("b"))).toCompletableFuture().join()).isEmpty();
    }

    @Test
    void healthReportUsesAuthenticatedTargetRatherThanEchoFields() {
        ProbeFixture fixture = new ProbeFixture(Runnable::run);
        when(fixture.invoker.invokeAuthenticated(any(), any(), any(), any(),
                eq(BrokerHealthReportResult.class), eq(500), any(), any()))
                .thenReturn(new BrokerHealthReportResult("other-cluster", "other-reporter", 9L, 8L, "wrong",
                        List.of(new BrokerHealthReportRow("b",
                                com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNHEALTHY, 10L, 30L))));
        try (MockedStatic<Instant> wall = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            wall.when(Instant::now).thenReturn(Instant.EPOCH);
            assertThat(fixture.peer.fetchHealthReport(fixture.target, new BrokerClusterId("cluster-a"), new ClusterRevision(1L), new RoutingEpoch(2L),
                    Set.of(new BrokerId("b"))).toCompletableFuture().join())
                    .singleElement().satisfies(observation -> {
                        assertThat(observation.observerBrokerId()).isEqualTo(fixture.target.id());
                        assertThat(observation.brokerId()).isEqualTo(new BrokerId("b"));
                        assertThat(observation.status()).isEqualTo(BrokerHealthStatus.UNHEALTHY);
                        assertThat(observation.observedAt()).isEqualTo(Instant.EPOCH.minusMillis(10));
                        assertThat(observation.unreachableBeginAt()).isEqualTo(Instant.EPOCH.minusMillis(40));
                    });
        }
        ArgumentCaptor<BrokerHealthReportParams> request = ArgumentCaptor.forClass(BrokerHealthReportParams.class);
        verify(fixture.invoker).invokeAuthenticated(eq("b:18020"), eq("broker.broker"), eq("fetchBrokerHealthReport"),
                request.capture(), eq(BrokerHealthReportResult.class), eq(500),
                eq("spiffe://im-chat/broker/b"), eq("spiffe://im-chat/broker/"));
        assertThat(request.getValue()).satisfies(params -> {
            assertThat(params.clusterId()).isEqualTo("cluster-a");
            assertThat(params.callerBrokerId()).isEqualTo("a");
            assertThat(params.receiverBrokerId()).isEqualTo("b");
            assertThat(params.revision()).isEqualTo(1L);
            assertThat(params.epoch()).isEqualTo(2L);
            assertThat(params.nonce()).isNotBlank();
            assertThat(params.brokerIds()).containsExactly("b");
        });
    }

    @Test
    void queuedReportSamplesOnceAfterParamsBeforeRpcAndPreservesNetworkAgeForEveryRow() {
        List<Runnable> tasks = new ArrayList<>();
        ProbeFixture fixture = new ProbeFixture(tasks::add);
        Instant requestAt = Instant.ofEpochSecond(100);
        Instant responseAt = requestAt.plusMillis(200);
        BrokerHealthReportResult response = new BrokerHealthReportResult("cluster-a", "b", 7L, 9L,
                "queued-nonce", List.of(
                new BrokerHealthReportRow("b", com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNHEALTHY, 10L, 30L),
                new BrokerHealthReportRow("c", com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNHEALTHY, 20L, 40L)));
        try (MockedStatic<GeneratorUtils> generator = Mockito.mockStatic(GeneratorUtils.class);
                MockedStatic<Instant> wall = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            generator.when(GeneratorUtils::nextUUID).thenAnswer(ignored -> {
                wall.verify(Instant::now, Mockito.never());
                return "queued-nonce";
            });
            AtomicReference<Instant> currentWall = new AtomicReference<>(requestAt);
            wall.when(Instant::now).thenAnswer(ignored -> currentWall.get());
            wall.clearInvocations();
            when(fixture.invoker.invokeAuthenticated(any(), any(), any(), any(),
                    eq(BrokerHealthReportResult.class), eq(500), any(), any())).thenAnswer(call -> {
                generator.verify(GeneratorUtils::nextUUID);
                wall.verify(Instant::now, Mockito.times(1));
                BrokerHealthReportParams params = call.getArgument(3);
                assertThat(params.clusterId()).isEqualTo("cluster-a");
                assertThat(params.callerBrokerId()).isEqualTo("a");
                assertThat(params.receiverBrokerId()).isEqualTo("b");
                assertThat(params.nonce()).isEqualTo("queued-nonce");
                assertThat(params.revision()).isEqualTo(7L);
                assertThat(params.epoch()).isEqualTo(9L);
                assertThat(params.brokerIds()).containsExactlyInAnyOrder("b", "c");
                assertThatThrownBy(() -> params.brokerIds().clear()).isInstanceOf(UnsupportedOperationException.class);
                // 模拟 RPC 耗时；返回后不得再取样并抹去该耗时。
                currentWall.set(responseAt);
                return response;
            });
            CompletableFuture<List<BrokerHealthObservation>> report = fixture.peer.fetchHealthReport(
                    fixture.target, new BrokerClusterId("cluster-a"), new ClusterRevision(7L), new RoutingEpoch(9L),
                    Set.of(new BrokerId("b"), new BrokerId("c"))).toCompletableFuture();
            assertThat(report).isNotDone();
            assertThat(tasks).hasSize(1);
            verifyNoInteractions(fixture.invoker);
            generator.verifyNoInteractions();
            wall.verify(Instant::now, Mockito.never());
            tasks.removeFirst().run();
            verify(fixture.invoker).invokeAuthenticated(eq("b:18020"), eq("broker.broker"), eq("fetchBrokerHealthReport"),
                    any(BrokerHealthReportParams.class), eq(BrokerHealthReportResult.class), eq(500),
                    eq("spiffe://im-chat/broker/b"), eq("spiffe://im-chat/broker/"));
            assertThat(report.join()).hasSize(2);
            for (int index = 0; index < 2; index++) {
                BrokerHealthObservation row = report.join().get(index);
                long ageMs = index == 0 ? 10L : 20L;
                long unhealthyMs = index == 0 ? 30L : 40L;
                assertThat(row.observedAt()).isEqualTo(requestAt.minusMillis(ageMs));
                assertThat(Duration.between(row.observedAt(), responseAt)).isEqualTo(Duration.ofMillis(ageMs + 200L));
                assertThat(Duration.between(row.unreachableBeginAt(), row.observedAt())).isEqualTo(Duration.ofMillis(unhealthyMs));
            }
            wall.verify(Instant::now, Mockito.times(1));
            generator.verify(GeneratorUtils::nextUUID);
            generator.verifyNoMoreInteractions();
        }
    }

    @Test
    void healthReportRejectsExcessAndUncommittedTargets() {
        ProbeFixture fixture = new ProbeFixture(Runnable::run);
        BrokerHealthReportRow b = new BrokerHealthReportRow("b",
                com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNKNOWN, 0L, 0L);
        BrokerHealthReportRow c = new BrokerHealthReportRow("c",
                com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNKNOWN, 0L, 0L);
        for (List<BrokerHealthReportRow> rows : List.of(List.of(b, c), List.of(c))) {
            when(fixture.invoker.invokeAuthenticated(any(), any(), any(), any(),
                    eq(BrokerHealthReportResult.class), eq(500), any(), any()))
                    .thenReturn(new BrokerHealthReportResult("cluster-a", "b", 1L, 2L, "expected", rows));
            Set<BrokerId> targets = Set.of(new BrokerId("b"));
            assertThatThrownBy(() -> fixture.peer.fetchHealthReport(fixture.target, new BrokerClusterId("cluster-a"), new ClusterRevision(1L), new RoutingEpoch(2L),
                    targets).toCompletableFuture().join())
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void healthReportPropagatesTransportFailure() {
        ProbeFixture fixture = new ProbeFixture(Runnable::run);
        BoltInvocationException failure = new BoltInvocationException(
                BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE, "unavailable", null);
        when(fixture.invoker.invokeAuthenticated(any(), any(), any(), any(),
                eq(BrokerHealthReportResult.class), eq(500), any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> fixture.peer.fetchHealthReport(fixture.target, new BrokerClusterId("cluster-a"), new ClusterRevision(1L), new RoutingEpoch(2L),
                Set.of(new BrokerId("b"))).toCompletableFuture().join()).hasCause(failure);
    }

    @Test
    void queuedProbePreservesTypedContextAndTransportBudgetWhenTaskExecutes() {
        List<Runnable> tasks = new ArrayList<>();
        Duration timeout = Duration.ofMillis(123);
        ProbeFixture fixture = new ProbeFixture(tasks::add, timeout);
        when(fixture.invoker.invokeAuthenticated(eq("b:18020"), eq("broker.broker"), eq("probeBroker"),
                any(BrokerProbeParams.class), eq(BrokerProbeResult.class), eq(123),
                eq("spiffe://im-chat/broker/b"), eq("spiffe://im-chat/broker/")))
                .thenAnswer(call -> {
                    BrokerProbeParams params = call.getArgument(3);
                    return new BrokerProbeResult(params.clusterId(), params.receiverBrokerId(), params.revision(),
                            params.epoch(), params.nonce());
                });

        CompletableFuture<BrokerHealthStatus> result = fixture.peer.probe(fixture.target,
                new BrokerClusterId("cluster-a"), new ClusterRevision(7L), new RoutingEpoch(9L))
                .toCompletableFuture();
        assertThat(result).isNotDone();
        assertThat(tasks).hasSize(1);
        verifyNoInteractions(fixture.invoker);

        tasks.removeFirst().run();

        assertThat(result.join()).isEqualTo(BrokerHealthStatus.HEALTHY);
        ArgumentCaptor<BrokerProbeParams> request = ArgumentCaptor.forClass(BrokerProbeParams.class);
        verify(fixture.invoker).invokeAuthenticated(eq("b:18020"), eq("broker.broker"), eq("probeBroker"),
                request.capture(), eq(BrokerProbeResult.class), eq(123),
                eq("spiffe://im-chat/broker/b"), eq("spiffe://im-chat/broker/"));
        BrokerProbeParams params = request.getValue();
        assertThat(params.clusterId()).isEqualTo("cluster-a");
        assertThat(params.revision()).isEqualTo(7L);
        assertThat(params.epoch()).isEqualTo(9L);
        assertThat(params.callerBrokerId()).isEqualTo("a");
        assertThat(params.receiverBrokerId()).isEqualTo("b");
        assertThat(params.nonce()).isNotBlank();
    }

    @Test
    void probeRejectsExecutorSaturationWithoutCallingTransport() {
        ProbeFixture fixture = new ProbeFixture(task -> { throw new RejectedExecutionException("full"); });
        assertThat(fixture.probe()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        verifyNoInteractions(fixture.invoker);
    }

    @Test
    void probeDoesNotInferTransportFromUnknownExceptionOrCause() {
        ProbeFixture fixture = new ProbeFixture(Runnable::run);
        when(fixture.invoker.invokeAuthenticated(eq("b:18020"), any(), any(), any(),
                eq(BrokerProbeResult.class), eq(500), eq("spiffe://im-chat/broker/b"),
                eq("spiffe://im-chat/broker/")))
                .thenThrow(new IllegalStateException("timeout", new BoltInvocationException(
                        BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE, "nested", null)));
        assertThat(fixture.probe()).isEqualTo(BrokerHealthStatus.UNKNOWN);
    }

    private static final class ProbeFixture {
        private final BoltInvoker invoker = mock(BoltInvoker.class);
        private final BrokerPeerAdapter peer;
        private final BrokerNode target = new BrokerNode(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"),
                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE);


        private ProbeFixture(Executor executor) {
            this(executor, Duration.ofMillis(500));
        }

        private ProbeFixture(Executor executor, Duration timeout) {
            BrokerFailureObservationProperties observation = new BrokerFailureObservationProperties();
            observation.setRpcTimeout(timeout);
            peer = new BrokerPeerAdapter(invoker, new BrokerProperties(), Runnable::run, Runnable::run,
                    new BrokerRoutingProperties(), observation, "a", executor, "spiffe://im-chat/broker/");
        }

        private BrokerHealthStatus probe() {
            return peer.probe(target, new BrokerClusterId("cluster-a"), new ClusterRevision(1L),
                    new RoutingEpoch(2L)).toCompletableFuture().join();
        }
    }

    @Test
    void installLeasePreservesGrantedPayload() {
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("installRouteLease"), eq(new RouteLeaseInstallParams(
                        "broker-b", 7L, 3L, 1L, "nonce-1", 100L, 42L)),
                eq(RouteLeaseInstallResult.class), eq(3000)))
                .thenReturn(new RouteLeaseInstallResult(true));
        BrokerPeerAdapter peer = new BrokerPeerAdapter(invoker, new BrokerProperties(),
                Runnable::run, Runnable::run, new BrokerRoutingProperties(), new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        RoutingLease lease = RoutingLease.builder().brokerId(new BrokerId("broker-b"))
                .routingEpoch(new RoutingEpoch(7L)).leaderTerm(new LeaderTerm(3L))
                .leaseSequence(new LeaseSequence(1L)).leaseNonce(new LeaseNonce("nonce-1"))
                .leaseDuration(Duration.ofNanos(100L)).quorumReadIndex(new QuorumReadIndex(42L)).build();

        assertThat(peer.installLease(target(), lease).toCompletableFuture().join()).isTrue();
        verify(invoker).invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("installRouteLease"), eq(new RouteLeaseInstallParams(
                        "broker-b", 7L, 3L, 1L, "nonce-1", 100L, 42L)),
                eq(RouteLeaseInstallResult.class), eq(3000));
    }

    @Test
    void routeLookupTimesOutWhenIoTaskDoesNotComplete() {
        BrokerRoutingProperties routing = new BrokerRoutingProperties();
        routing.setRouteLookupTimeout(Duration.ofMillis(20));
        BrokerPeerAdapter peer = new BrokerPeerAdapter(mock(BoltInvoker.class), new BrokerProperties(),
                Runnable::run, ignored -> { }, routing, new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        com.co.kc.imchat.broker.sdk.cluster.BrokerNode broker =
                new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("broker-b", "broker-b.example:19000",
                        com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                        BrokerRoutingState.ACTIVE);

        assertThatThrownBy(() -> peer.lookupRoutes(broker, new UserId(42L), new RoutingEpoch(7L))
                .toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void fenceRequestsCanRunConcurrentlyOnPeerExecutor() throws Exception {
        BrokerProperties properties = new BrokerProperties();
        properties.getPeerCall().setMaxConcurrentRequests(2);
        ThreadPoolExecutor executor = new BrokerClusterConfiguration()
                .brokerPeerExecutor(properties);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(any(String.class), eq("broker.broker"), eq("fenceRoutingEpoch"),
                any(RoutingFenceParams.class), eq(RoutingFenceAckResult.class), eq(3000)))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test fence RPC was not released");
                    }
                    RoutingFenceParams params = invocation.getArgument(3);
                    String address = invocation.getArgument(0);
                    String id = address.equals("broker-a.example:19000") ? "broker-a" : "broker-b";
                    return new RoutingFenceAckResult(id, params.routingEpoch(),
                            params.leaderTerm(), 9L, params.operationId());
                });
        RoutingEpochFence fence = RoutingEpochFence.builder()
                .operationId(new ClusterOperationId("fence-1"))
                .epoch(new RoutingEpoch(7L))
                .leaderTerm(new LeaderTerm(1L))
                .quorumReadIndex(new QuorumReadIndex(1L))
                .fencedBrokerIds(List.of(new BrokerId("broker-a"), new BrokerId("broker-b"))).build();
        BrokerPeerAdapter peer = new BrokerPeerAdapter(invoker, properties, executor,
                Runnable::run, new BrokerRoutingProperties(), new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        try {
            CompletableFuture<FenceAck> first = peer.fence(BrokerNode.discovered(new BrokerId("broker-a"),
                    new BrokerRouteEndpoint("broker-a.example:19000")), fence).toCompletableFuture();
            CompletableFuture<FenceAck> second = peer.fence(target(), fence).toCompletableFuture();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).brokerId()).isEqualTo(new BrokerId("broker-a"));
            assertThat(second.get(5, TimeUnit.SECONDS).brokerId()).isEqualTo(new BrokerId("broker-b"));
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void routeLookupRunsOnDedicatedExecutorAndPreservesDeadlineAndHop() {
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.connection"),
                eq("lookupConnectionRoutes"), any(RouteLookupParams.class),
                eq(RouteLookupResult.class), eq(123)))
                .thenReturn(new RouteLookupResult(List.of(
                        new RouteLocationDTO("gateway-a", "gateway.example", 18012, 9L, 7L))));
        List<Runnable> lookupTasks = new ArrayList<>();
        BrokerRoutingProperties routing = new BrokerRoutingProperties();
        routing.setRouteLookupTimeout(Duration.ofMillis(123));
        BrokerPeerAdapter peer = new BrokerPeerAdapter(invoker, new BrokerProperties(), Runnable::run,
                lookupTasks::add, routing, new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        com.co.kc.imchat.broker.sdk.cluster.BrokerNode broker =
                new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("broker-b", "broker-b.example:19000",
                        com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                        BrokerRoutingState.ACTIVE);

        CompletableFuture<List<RouteLocation>> response = peer.lookupRoutes(
                broker, new UserId(42L), new RoutingEpoch(7L)).toCompletableFuture();
        assertThat(response).isNotDone();
        verifyNoInteractions(invoker);
        long before = System.currentTimeMillis();
        lookupTasks.removeFirst().run();
        long after = System.currentTimeMillis();
        assertThat(response.join()).containsExactly(
                new RouteLocation("gateway-a", "gateway.example", 18012, 9L, 7L));
        ArgumentCaptor<RouteLookupParams> request =
                ArgumentCaptor.forClass(RouteLookupParams.class);
        Mockito.verify(invoker).invoke(eq("broker-b.example:19000"), eq("broker.connection"),
                eq("lookupConnectionRoutes"), request.capture(), eq(RouteLookupResult.class), eq(123));
        assertThat(request.getValue().requesterBrokerId()).isEqualTo("broker-a");
        assertThat(request.getValue().targetUserId()).isEqualTo(42L);
        assertThat(request.getValue().routingEpoch()).isEqualTo(7L);
        assertThat(request.getValue().hopCount()).isEqualTo(1);
        assertThat(request.getValue().expireAtMs()).isBetween(before + 123L, after + 123L);
    }

    @Test
    void cleanupUsesPeerExecutorAndPreservesOperationPayload() {
        BoltInvoker invoker = mock(BoltInvoker.class);
        BrokerRoutingProperties routing = new BrokerRoutingProperties();
        routing.setDrainCleanupRpcTimeout(Duration.ofMillis(1234));
        DrainRouteCleanupParams params = new DrainRouteCleanupParams(
                "broker-a", "drain-1", 7L, 8L, 10_000L, List.of());
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.connection"),
                eq("cleanupDrainRoutes"), eq(params), eq(DrainRouteCleanupResult.class), eq(1234)))
                .thenReturn(new DrainRouteCleanupResult(DrainRouteCleanupResult.Status.CLEANED,
                        12L, 0L, true, null),
                        new DrainRouteCleanupResult(DrainRouteCleanupResult.Status.REJECTED,
                                0L, 0L, false, "wrong watermark"));
        List<Runnable> tasks = new ArrayList<>();
        BrokerPeerAdapter peer = new BrokerPeerAdapter(invoker, new BrokerProperties(), tasks::add,
                Runnable::run, routing, new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        BrokerClusterOperation operation = mock(BrokerClusterOperation.class);
        when(operation.id()).thenReturn(new ClusterOperationId("drain-1"));
        when(operation.targetBrokerId()).thenReturn(new BrokerId("broker-b"));
        when(operation.acceptedEpoch()).thenReturn(new RoutingEpoch(7L));
        when(operation.resultEpoch()).thenReturn(new RoutingEpoch(8L));
        when(operation.drainProgress()).thenReturn(new DrainProgress(List.of(),
                Instant.ofEpochMilli(10_000L), null));

        CompletableFuture<RouteCleanupResult> result = peer.cleanupRoutes(target(), operation).toCompletableFuture();
        assertThat(result).isNotDone();
        verifyNoInteractions(invoker);
        tasks.removeFirst().run();
        assertThat(result.join()).isEqualTo(new RouteCleanupResult(new RouteCount(12L), RouteCount.ZERO, true));
        CompletableFuture<RouteCleanupResult> rejected = peer.cleanupRoutes(target(), operation).toCompletableFuture();
        tasks.removeFirst().run();
        assertThatThrownBy(rejected::join).hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wrong watermark");
    }

    @Test
    void fenceRejectsWrongIdentityAndExecutorSaturation() {
        BoltInvoker invoker = mock(BoltInvoker.class);
        RoutingEpochFence fence = RoutingEpochFence.builder()
                .operationId(new ClusterOperationId("fence-1"))
                .epoch(new RoutingEpoch(7L))
                .leaderTerm(new LeaderTerm(1L))
                .quorumReadIndex(new QuorumReadIndex(1L))
                .fencedBrokerIds(List.of(new BrokerId("broker-b"))).build();
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("fenceRoutingEpoch"), eq(new RoutingFenceParams("fence-1", 7L, 1L, 1L)),
                eq(RoutingFenceAckResult.class), eq(3000)))
                .thenReturn(new RoutingFenceAckResult("broker-b", 7L, 1L, 9L, "fence-1"),
                        new RoutingFenceAckResult("broker-a", 7L, 1L, 9L, "fence-1"));
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        BrokerPeerAdapter peer = new BrokerPeerAdapter(invoker, new BrokerProperties(), tasks::add,
                Runnable::run, new BrokerRoutingProperties(), new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        CompletableFuture<FenceAck> accepted = peer.fence(target(), fence).toCompletableFuture();
        assertThat(accepted).isNotDone();
        tasks.removeFirst().run();
        assertThat(accepted.join().brokerId()).isEqualTo(new BrokerId("broker-b"));
        CompletableFuture<FenceAck> rejected = peer.fence(target(), fence).toCompletableFuture();
        tasks.removeFirst().run();
        assertThatThrownBy(rejected::join).hasCauseInstanceOf(IllegalStateException.class);

        Executor saturated = task -> { throw new RejectedExecutionException("peer queue full"); };
        BrokerPeerAdapter unavailable = new BrokerPeerAdapter(invoker, new BrokerProperties(), saturated,
                Runnable::run, new BrokerRoutingProperties(), new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        assertThatThrownBy(() -> unavailable.fence(target(), fence).toCompletableFuture().join())
                .hasCauseInstanceOf(RejectedExecutionException.class);
    }

    private BrokerNode target() {
        return BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("broker-b.example:19000"));
    }

    @Test
    void prepareLeaseRejectsAnotherBrokerIdentity() {
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("prepareRouteLease"), eq(new RouteLeasePrepareParams("lease-1", 7L)),
                eq(RouteLeasePrepareResult.class), eq(3000)))
                .thenReturn(new RouteLeasePrepareResult("broker-b", "nonce-1"),
                        new RouteLeasePrepareResult("broker-other", "nonce-2"));
        BrokerPeerAdapter peer = new BrokerPeerAdapter(invoker, new BrokerProperties(),
                Runnable::run, Runnable::run, new BrokerRoutingProperties(), new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        BrokerNode target = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("broker-b.example:19000"));

        assertThat(peer.prepareLease(target, new RoutingEpoch(7L), new ClusterOperationId("lease-1"))
                .toCompletableFuture().join()).isEqualTo(new LeaseNonce("nonce-1"));
        assertThatThrownBy(() -> peer.prepareLease(target, new RoutingEpoch(7L),
                new ClusterOperationId("lease-1")).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease prepare response does not match brokerNode broker");
    }

    @Test
    void quorumReadIndexPreservesConfiguredTimeoutAndTransportFailure() {
        BoltInvoker invoker = mock(BoltInvoker.class);
        BrokerProperties properties = new BrokerProperties();
        properties.getPeerCall().setTimeoutMs(1234);
        IllegalStateException failure = new IllegalStateException("peer unavailable");
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("fetchQuorumReadIndex"), eq(new QuorumReadIndexParams()),
                eq(QuorumReadIndexResult.class), eq(1234))).thenThrow(failure);
        BrokerPeerAdapter peer = new BrokerPeerAdapter(invoker, properties,
                ignored -> { throw new AssertionError("quorum read must remain synchronous"); },
                Runnable::run, new BrokerRoutingProperties(), new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");

        CompletableFuture<Long> result = peer.fetchQuorumReadIndex(target()).toCompletableFuture();

        assertThat(result).isCompletedExceptionally();
        assertThatThrownBy(result::join).isInstanceOf(CompletionException.class).hasCause(failure);
        verify(invoker).invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("fetchQuorumReadIndex"), eq(new QuorumReadIndexParams()),
                eq(QuorumReadIndexResult.class), eq(1234));
    }

    @Test
    void remoteAppliedIndexRequiresTargetIdentityAndNonnegativeIndex() {
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("fetchQuorumReadIndex"), eq(new QuorumReadIndexParams()),
                eq(QuorumReadIndexResult.class), eq(3000)))
                .thenReturn(new QuorumReadIndexResult("broker-b", 12L),
                        new QuorumReadIndexResult("broker-c", 20L),
                        new QuorumReadIndexResult("broker-b", -1L));
        BrokerPeerAdapter peer = new BrokerPeerAdapter(invoker, new BrokerProperties(),
                Runnable::run, Runnable::run, new BrokerRoutingProperties(), new BrokerFailureObservationProperties(), "broker-a", Runnable::run, "spiffe://im-chat/broker/");
        BrokerNode target = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("broker-b.example:19000"));

        assertThat(peer.fetchQuorumReadIndex(target).toCompletableFuture().join()).isEqualTo(12L);
        for (int index = 0; index < 2; index++) {
            assertThatThrownBy(() -> peer.fetchQuorumReadIndex(target).toCompletableFuture().join())
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not match target node");
        }
    }
}
