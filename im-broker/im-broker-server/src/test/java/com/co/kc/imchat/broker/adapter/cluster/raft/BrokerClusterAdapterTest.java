package com.co.kc.imchat.broker.adapter.cluster.raft;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayHost;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPort;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPrincipalId;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;

import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;

import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;

import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.sdk.model.params.DrainRouteCleanupParams;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerRouteLookupParams;
import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.sdk.model.dto.RouteLocationDTO;
import com.co.kc.imchat.broker.sdk.model.result.RouteLookupResult;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import org.mockito.ArgumentCaptor;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal;
import com.co.kc.imchat.broker.support.BrokerClusterCommandFixture;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.result.QuorumReadIndexResult;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import com.co.kc.imchat.broker.infrastructure.config.BrokerClusterConfiguration;
import java.util.concurrent.RejectedExecutionException;
import java.util.ArrayDeque;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.broker.sdk.model.result.RoutingFenceAckResult;
import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeasePrepareResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeaseInstallResult;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.summary;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static org.awaitility.Awaitility.await;

class BrokerClusterAdapterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void leaseRpcPreservesPayloadAndRejectsWrongPrepareIdentity() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("lease-peer"), List.of(endpoint(port)));
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(eq("broker-a.example:19000"), eq("broker.broker"),
                eq("prepareRouteLease"), eq(new RouteLeasePrepareParams("lease-1", 7L)),
                eq(RouteLeasePrepareResult.class), eq(3000)))
                .thenReturn(new RouteLeasePrepareResult("broker-a", "nonce-1"),
                        new RouteLeasePrepareResult("broker-other", "nonce-2"));
        RoutingLease lease = RoutingLease.builder().brokerId(new BrokerId("broker-a"))
                .routingEpoch(new RoutingEpoch(7L)).leaderTerm(term(3L)).leaseSequence(leaseSequence(1L))
                .leaseNonce(new LeaseNonce("nonce-1")).leaseDuration(Duration.ofNanos(100L))
                .quorumReadIndex(readIndex(42L)).build();
        when(invoker.invoke(eq("broker-a.example:19000"), eq("broker.broker"),
                eq("installRouteLease"), eq(new RouteLeaseInstallParams(
                        "broker-a", 7L, 3L, 1L, "nonce-1", 100L, 42L)),
                eq(RouteLeaseInstallResult.class), eq(3000)))
                .thenReturn(new RouteLeaseInstallResult(true));
        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(properties,
                state(7L, 7L, active("broker-a")), invoker, new BrokerProperties(), Runnable::run,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), new BrokerRoutingProperties(), Runnable::run,
                Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()))) {
            com.co.kc.imchat.broker.domain.cluster.model.BrokerNode broker =
                    adapter.getCluster().findNode(new BrokerId("broker-a")).orElseThrow();
            assertThat(adapter.prepareLease(broker, new RoutingEpoch(7L), new ClusterOperationId("lease-1"))
                    .toCompletableFuture().join()).isEqualTo(new LeaseNonce("nonce-1"));
            assertThat(adapter.installLease(broker, lease).toCompletableFuture().join()).isTrue();
            assertThatThrownBy(() -> adapter.prepareLease(
                    broker, new RoutingEpoch(7L), new ClusterOperationId("lease-1")).toCompletableFuture().join())
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lease prepare response does not match target broker");
        }
    }

    @Test
    void gatewayWritePreservesPayloadAndPerTargetSuccessFailureMetrics() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("gateway-peer"), List.of(endpoint(port)));
        GatewayClient gatewayClient = mock(GatewayClient.class);
        when(gatewayClient.writeFrame(any(), any()))
                .thenReturn(new GatewayFrameWriteResult(List.of("connection-a"), List.of()))
                .thenThrow(new IllegalStateException("gateway unavailable"));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        GatewayRegistration gateway = new GatewayRegistration(new GatewayId("gateway-a"), new GatewayHost("gateway.example"), new GatewayPort(18012), new GatewayPrincipalId("identity-a"), Instant.EPOCH, Instant.EPOCH);
        FrameResponse frame = new FrameResponse("1", FrameType.PUSH, "message.private.sent",
                null, null, null, null, java.util.Map.of());
        FrameDispatchCommand command = new FrameDispatchCommand(42L, frame, "delivery-1", 7L);

        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(properties,
                state(7L, 7L, active("broker-a")), mock(BoltInvoker.class), new BrokerProperties(), Runnable::run,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), new BrokerRoutingProperties(), Runnable::run,
                Runnable::run, gatewayClient, new MetricsCollector(metrics))) {
            assertThat(adapter.dispatchFrame(gateway, command).toCompletableFuture().join())
                    .extracting("connectionId").containsExactly("connection-a");
            assertThatThrownBy(() -> adapter.dispatchFrame(gateway, command).toCompletableFuture().join())
                    .hasCauseInstanceOf(IllegalStateException.class);
            ArgumentCaptor<GatewayFrameWriteParams> requests = ArgumentCaptor.forClass(GatewayFrameWriteParams.class);
            verify(gatewayClient, times(2)).writeFrame(any(), requests.capture());
            assertThat(requests.getAllValues()).allSatisfy(request -> {
                assertThat(request.userId()).isEqualTo(42L);
                assertThat(request.deliveryId()).isEqualTo("delivery-1");
                assertThat(request.deliveryAttemptId()).isNotBlank();
                assertThat(request.frame()).isSameAs(frame);
            });
            assertThat(metrics.counter("im.broker.gateway.frame.write", "outcome", "success").count()).isEqualTo(1);
            assertThat(metrics.counter("im.broker.gateway.frame.write", "outcome", "failure").count()).isEqualTo(1);
            assertThat(metrics.timer("im.broker.gateway.frame.write.duration").count()).isEqualTo(2);
        }
    }

    @Test
    void routeLookupUsesConfiguredExecutorAndPreservesReadOnlyRequest() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("lookup-peer"), List.of(endpoint(port)));
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.connection"),
                eq("lookupConnectionRoutes"), any(RouteLookupParams.class),
                eq(RouteLookupResult.class), eq(123)))
                .thenReturn(new RouteLookupResult(List.of(
                        new RouteLocationDTO("gateway-a", "gateway.example", 18012, 9L, 7L))));
        List<Runnable> queries = new java.util.ArrayList<>();
        BrokerRoutingProperties routingProperties = new BrokerRoutingProperties();
        routingProperties.setRouteLookupTimeout(Duration.ofMillis(123));
        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(properties,
                state(7L, 7L, active("broker-a")), invoker, new BrokerProperties(), Runnable::run,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), routingProperties, queries::add,
                Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()))) {
            CompletableFuture<List<RouteLocation>> response = adapter.lookupRoutes(
                    new BrokerRouteLookupParams(new BrokerNode("broker-b", "broker-b.example:19000",
                            BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                            new UserId(42L), new RoutingEpoch(7L))).toCompletableFuture();
            assertThat(response).isNotDone();
            verifyNoInteractions(invoker);
            long beforeInvocation = System.currentTimeMillis();
            queries.removeFirst().run();
            long afterInvocation = System.currentTimeMillis();
            assertThat(response.join()).containsExactly(
                    new RouteLocation("gateway-a", "gateway.example", 18012, 9L, 7L));
            ArgumentCaptor<RouteLookupParams> request = ArgumentCaptor.forClass(RouteLookupParams.class);
            verify(invoker).invoke(eq("broker-b.example:19000"), eq("broker.connection"),
                    eq("lookupConnectionRoutes"), request.capture(), eq(RouteLookupResult.class), eq(123));
            assertThat(request.getValue().targetUserId()).isEqualTo(42L);
            assertThat(request.getValue().hopCount()).isEqualTo(1);
            assertThat(request.getValue().deadlineEpochMillis())
                    .isBetween(beforeInvocation + 123L, afterInvocation + 123L);
        }
    }

    @Test
    void routeLookupTimesOutWhenTheIoTaskDoesNotComplete() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("lookup-timeout"), List.of(endpoint(port)));
        BrokerRoutingProperties routingProperties = new BrokerRoutingProperties();
        routingProperties.setRouteLookupTimeout(Duration.ofMillis(20));
        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(properties,
                state(7L, 7L, active("broker-a")), mock(BoltInvoker.class), new BrokerProperties(), Runnable::run,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), routingProperties,
                ignored -> { }, Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()))) {
            CompletableFuture<List<RouteLocation>> response = adapter.lookupRoutes(
                    new BrokerRouteLookupParams(new BrokerNode("broker-b", "broker-b.example:19000",
                            BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                            new UserId(42L), new RoutingEpoch(7L))).toCompletableFuture();

            assertThatThrownBy(response::join).isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
        }
    }

    @Test
    void cleanupUsesPeerExecutorAndPreservesOperationPayloadAndRejection() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("cleanup-peer"), List.of(endpoint(port)));
        BrokerRoutingProperties routing = new BrokerRoutingProperties();
        routing.setDrainCleanupRpcTimeout(Duration.ofMillis(1234));
        BoltInvoker invoker = mock(BoltInvoker.class);
        DrainRouteCleanupParams params = new DrainRouteCleanupParams(
                "broker-a", "drain-1", 7L, 8L, 10_000L, List.of());
        when(invoker.invoke(eq("broker-a.example:19000"), eq("broker.connection"),
                eq("cleanupDrainRoutes"), eq(params), eq(DrainRouteCleanupResult.class), eq(1234)))
                .thenReturn(new DrainRouteCleanupResult(DrainRouteCleanupResult.Status.CLEANED, 12L, 0L, true, null),
                        new DrainRouteCleanupResult(DrainRouteCleanupResult.Status.REJECTED, 0L, 0L, false, "wrong watermark"));
        BrokerClusterOperation operation = mock(BrokerClusterOperation.class);
        when(operation.id()).thenReturn(new ClusterOperationId("drain-1"));
        when(operation.targetBrokerId()).thenReturn(new BrokerId("broker-a"));
        when(operation.acceptedEpoch()).thenReturn(new RoutingEpoch(7L));
        when(operation.resultEpoch()).thenReturn(new RoutingEpoch(8L));
        when(operation.drainProgress()).thenReturn(new DrainProgress(List.of(), Instant.ofEpochMilli(10_000L), null));
        List<Runnable> tasks = new java.util.ArrayList<>();

        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(properties,
                state(7L, 7L, active("broker-a")), invoker, new BrokerProperties(), tasks::add,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), routing, tasks::add,
                Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()))) {
            CompletableFuture<RouteCleanupResult> cleanup = adapter.cleanupRoutes(operation).toCompletableFuture();
            assertThat(cleanup).isNotDone();
            verifyNoInteractions(invoker);
            tasks.removeFirst().run();
            assertThat(cleanup.join()).isEqualTo(new RouteCleanupResult(new RouteCount(12L), RouteCount.ZERO, true));

            CompletableFuture<RouteCleanupResult> rejected = adapter.cleanupRoutes(operation).toCompletableFuture();
            tasks.removeFirst().run();
            assertThatThrownBy(rejected::join).hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("wrong watermark");
        }
    }

    @Test
    void fenceRequestIsScheduledAndMapsCommittedEndpointAndAck() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("fence-peer"), List.of(endpoint(port)));
        BrokerClusterState initial = state(0L, 7L, active("broker-a"), active("broker-b"));
        RoutingEpochFence fence = fenceFor(initial, "fence-1");
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("fenceRoutingEpoch"), eq(new RoutingFenceParams("fence-1", 7L, 1L, 1L)),
                eq(RoutingFenceAckResult.class), eq(3000)))
                .thenReturn(new RoutingFenceAckResult("broker-b", 7L, 1L, 9L, "fence-1"),
                        new RoutingFenceAckResult("broker-a", 7L, 1L, 9L, "fence-1"));
        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(
                properties, initial, invoker, new BrokerProperties(), tasks::add,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), new BrokerRoutingProperties(), Runnable::run,
                Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()))) {
            CompletableFuture<FenceAck> response = adapter.fenceBroker(new BrokerId("broker-b"), fence)
                    .toCompletableFuture();
            assertThat(response).isNotDone();
            assertThat(tasks).hasSize(1);
            verifyNoInteractions(invoker);
            tasks.remove().run();
            FenceAck ack = response.join();
            assertThat(ack.brokerId()).isEqualTo(new BrokerId("broker-b"));
            assertThat(ack.routingEpoch()).isEqualTo(fence.epoch());
            assertThat(ack.leaderTerm()).isEqualTo(fence.leaderTerm());
            assertThat(ack.operationId()).isEqualTo(fence.operationId());
            assertThat(ack.fencedLeaseSequence().value()).isEqualTo(9L);
            CompletableFuture<FenceAck> mismatched = adapter.fenceBroker(new BrokerId("broker-b"), fence)
                    .toCompletableFuture();
            tasks.remove().run();
            assertThatThrownBy(mismatched::join)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("fence response does not match target broker");
        }
    }

    @Test
    void rejectedFenceTaskReturnsFailedStageWithoutExecutingRpcInline() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("fence-rejected"), List.of(endpoint(port)));
        BoltInvoker invoker = mock(BoltInvoker.class);
        Executor rejected = task -> { throw new RejectedExecutionException("peer queue full"); };
        BrokerClusterState initial = state(0L, 7L, active("broker-a"));
        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(
                properties, initial, invoker, new BrokerProperties(), rejected,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), new BrokerRoutingProperties(), Runnable::run,
                Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()))) {
            CompletableFuture<FenceAck> response = adapter.fenceBroker(
                    new BrokerId("broker-a"), fenceFor(initial, "fence-1")).toCompletableFuture();
            assertThatThrownBy(response::join).hasCauseInstanceOf(RejectedExecutionException.class);
            verifyNoInteractions(invoker);
        }
    }

    @Test
    void frozenHoldersCanExecuteFenceRpcConcurrently() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("fence-concurrent"), List.of(endpoint(port)));
        BrokerProperties brokerProperties = new BrokerProperties();
        brokerProperties.getPeerCall().setMaxConcurrentRequests(2);
        ThreadPoolExecutor executor = new BrokerClusterConfiguration().brokerPeerExecutor(brokerProperties);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(anyString(), eq("broker.broker"), eq("fenceRoutingEpoch"),
                any(RoutingFenceParams.class), eq(RoutingFenceAckResult.class), eq(3000)))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test fence RPC was not released");
                    }
                    RoutingFenceParams params = invocation.getArgument(3);
                    String address = invocation.getArgument(0);
                    String brokerId = address.equals("broker-a.example:19000") ? "broker-a" : "broker-b";
                    return new RoutingFenceAckResult(brokerId, params.routingEpoch(),
                            params.leaderTerm(), 9L, params.operationId());
                });
        BrokerClusterState initial = state(0L, 7L, active("broker-a"), active("broker-b"));
        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(
                properties, initial, invoker, brokerProperties, executor,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), new BrokerRoutingProperties(), Runnable::run,
                Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()))) {
            RoutingEpochFence fence = fenceFor(initial, "fence-1");
            CompletableFuture<FenceAck> first = adapter.fenceBroker(new BrokerId("broker-a"), fence)
                    .toCompletableFuture();
            CompletableFuture<FenceAck> second = adapter.fenceBroker(new BrokerId("broker-b"), fence)
                    .toCompletableFuture();
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
    void remoteAppliedIndexRejectsWrongBrokerAndNegativeIndex() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("remote-progress"), List.of(endpoint(port)));
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(eq("broker-b.example:19000"), eq("broker.broker"),
                eq("fetchQuorumReadIndex"), eq(new QuorumReadIndexParams()),
                eq(QuorumReadIndexResult.class), eq(3000)))
                .thenReturn(new QuorumReadIndexResult("broker-b", 12L),
                        new QuorumReadIndexResult("broker-c", 20L),
                        new QuorumReadIndexResult("broker-b", -1L));
        try (BrokerClusterAdapter adapter = new BrokerClusterAdapter(properties,
                state(0L, 0L, active("broker-a"), discovered("broker-b")), invoker, new BrokerProperties(), Runnable::run,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), new BrokerRoutingProperties(), Runnable::run,
                Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()))) {
            BrokerId targetBrokerId = new BrokerId("broker-b");
            assertThat(adapter.fetchQuorumReadIndex(adapter.getCluster().findNode(targetBrokerId).orElseThrow())
                    .toCompletableFuture().join()).isEqualTo(12L);
            for (int index = 0; index < 2; index++) {
                assertThatThrownBy(() -> adapter.fetchQuorumReadIndex(
                        adapter.getCluster().findNode(targetBrokerId).orElseThrow()).toCompletableFuture().join())
                        .hasCauseInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("does not match target node");
            }
        }
    }

    @Test
    void invalidCompletionTimeDoesNotPreventLaterRaftCommands() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("completion-time"), List.of(endpoint(port)));
        try (BrokerClusterAdapter adapter = createAdapter(properties,
                state(0L, 0L, active("broker-a")))) {
            await().atMost(Duration.ofSeconds(10)).until(adapter.getLocalBroker()::isLeader);
            assertThat(adapter.getLocalBroker()).isSameAs(adapter.getLocalBroker());
            assertThat(submitTopologyChange(adapter, BrokerClusterCommandFixture.topology(
                    "join", "digest", 0L, "ADD_BROKER", "broker-b",
                    List.of(active("broker-a"), discovered("broker-b")), 1_000L)).accepted()).isTrue();

            BrokerClusterConsensusResult rejected = adapter.submitChange(BrokerClusterCommandFixture.advanceOperation(
                    "join", "digest", BrokerClusterOperationPhase.RAFT_COMMITTED,
                    BrokerClusterOperationPhase.SUCCEEDED, null, 999L))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(rejected.status()).isEqualTo(BrokerClusterConsensusStatus.REJECTED);
            assertThat(adapter.findOperation(new ClusterOperationId("join")).orElseThrow().phase())
                    .isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);

            BrokerClusterConsensusResult completed = adapter.submitChange(BrokerClusterCommandFixture.advanceOperation(
                    "join", "digest", BrokerClusterOperationPhase.RAFT_COMMITTED,
                    BrokerClusterOperationPhase.SUCCEEDED, null, 1_001L))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(completed.status()).isEqualTo(BrokerClusterConsensusStatus.APPLIED);
            assertThat(adapter.findOperation(new ClusterOperationId("join")).orElseThrow().phase())
                    .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        }
    }

    @Test
    void commitsSnapshotsAndRestartsSingleVoterRepository() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("single-voter"), List.of(endpoint(port)));
        BrokerClusterState initialState = state(0L, 0L, active("broker-a"));

        try (BrokerClusterAdapter repository =
                     createAdapter(properties, initialState)) {
            await().atMost(Duration.ofSeconds(10)).until(repository.getLocalBroker()::isLeader);
            BrokerClusterConsensusResult result = submitTopologyChange(repository,
                    BrokerClusterCommandFixture.topology(
                            "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                            List.of(active("broker-a"), discovered("broker-b")), 1_000L));

            assertThat(result.status()).isEqualTo(BrokerClusterConsensusStatus.APPLIED);
            assertThat(repository.createSnapshot().toCompletableFuture().join().applied()).isTrue();
        }

        try (BrokerClusterAdapter recovered =
                     createAdapter(properties, initialState)) {
            await().atMost(Duration.ofSeconds(10)).until(recovered.getLocalBroker()::isLeader);
            assertThat(recovered.committedState().revision()).isEqualTo(2L);
            assertThat(recovered.findOperation(new ClusterOperationId("operation-1"))).isPresent();
            BrokerClusterConsensusResult duplicate = recovered.submitChange(BrokerClusterCommandFixture.commitOperation(
                    BrokerClusterCommandFixture.topology(
                            "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                            List.of(active("broker-a"), discovered("broker-b")), 1_000L)))
                    .toCompletableFuture().join();
            assertThat(duplicate.status()).isEqualTo(BrokerClusterConsensusStatus.DUPLICATE);
        }
    }

    @Test
    void recoveryCommandsValidateTimeoutAndRestoreDegradedIntentAfterRestart() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("recovery"), List.of(endpoint(port)));
        BrokerClusterState initialState = state(0L, 4L, active("broker-a"), active("broker-b"));
        ClusterOperationId operationId = new ClusterOperationId("failure-1");
        try (BrokerClusterAdapter adapter = createAdapter(properties, initialState)) {
            await().atMost(Duration.ofSeconds(10)).until(adapter.getLocalBroker()::isLeader);
            DrainProgress progress = new DrainProgress(List.of(checkpoint("gateway-a", 900L)),
                    Instant.ofEpochMilli(2_000L), null);
            assertThat(submitTopologyChange(adapter, BrokerClusterCommandFixture.topology(
                    "failure-1", "digest-1", 0L, "FAIL_BROKER", "broker-b",
                    List.of(active("broker-a"), new BrokerNode("broker-b", "broker-b.example:19000",
                            BrokerMembershipRole.VOTER, BrokerRoutingState.INACTIVE)), 1_000L, progress)).accepted()).isTrue();
            assertThat(adapter.submitChange(BrokerClusterCommandFixture.advanceOperation(
                    "failure-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                    BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 1_100L))
                    .toCompletableFuture().join().accepted()).isTrue();
            assertThat(adapter.submitChange(BrokerClusterCommandFixture.advanceOperation(
                    "failure-1", "digest-1", BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                    BrokerClusterOperationPhase.REBUILDING_ROUTES, null, 1_200L))
                    .toCompletableFuture().join().accepted()).isTrue();
            BrokerClusterOperation operation = adapter.findOperation(operationId).orElseThrow();
            assertThat(adapter.saveRecoveryEvidence(operation, progress.summarizeRebuild(), Instant.ofEpochMilli(1_500L))
                    .toCompletableFuture().join().accepted()).isTrue();
            assertThat(adapter.verifyRouteRecovery(operation, Duration.ofSeconds(10), Instant.ofEpochMilli(10_999L))
                    .toCompletableFuture().join().accepted()).isFalse();
            BrokerClusterConsensusResult verified = adapter.verifyRouteRecovery(
                    operation, Duration.ofSeconds(10), Instant.ofEpochMilli(11_000L)).toCompletableFuture().join();
            assertThat(verified.accepted()).isTrue();
            assertThat(verified.operation().error().value()).contains("gateway-a");
            assertThat(adapter.createSnapshot().toCompletableFuture().join().applied()).isTrue();
        }
        try (BrokerClusterAdapter restored = createAdapter(properties, initialState)) {
            await().atMost(Duration.ofSeconds(10)).until(restored.getLocalBroker()::isLeader);
            BrokerClusterOperation operation = restored.findOperation(operationId).orElseThrow();
            assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.VERIFYING);
            BrokerClusterConsensusResult completed = restored.completeRouteRecovery(operation, Instant.ofEpochMilli(11_001L))
                    .toCompletableFuture().join();
            assertThat(completed.operation().phase()).isEqualTo(BrokerClusterOperationPhase.DEGRADED);
            assertThat(completed.operation().error()).isEqualTo(operation.error());
            assertThat(restored.completeRouteRecovery(operation, Instant.ofEpochMilli(12_000L))
                    .toCompletableFuture().join().status()).isEqualTo(BrokerClusterConsensusStatus.DUPLICATE);
        }
    }

    @Test
    void threeVotersReplicateButMinorityCannotCommit() throws Exception {
        List<Integer> ports = List.of(availablePort(), availablePort(), availablePort());
        List<String> voters = ports.stream().map(this::endpoint).toList();
        BrokerClusterState initialState = state(0L, 0L,
                active("broker-a"), active("broker-b"), active("broker-c"));
        List<BrokerClusterAdapter> repositories = new ArrayList<>();
        try {
            for (int index = 0; index < ports.size(); index++) {
                BrokerRaftProperties properties = properties("broker-" + index, ports.get(index),
                        temporaryDirectory.resolve("three-voter-" + index), voters);
                properties.setCommandTimeoutMillis(800);
                repositories.add(createAdapter(properties, initialState));
            }
            BrokerClusterAdapter leader = awaitLeader(repositories);
            BrokerClusterConsensusResult committed = submitTopologyChange(leader,
                    BrokerClusterCommandFixture.topology(
                            "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-d",
                            List.of(active("broker-a"), active("broker-b"), active("broker-c"),
                                    discovered("broker-d")), 1_000L));
            assertThat(committed.status()).isEqualTo(BrokerClusterConsensusStatus.APPLIED);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> repositories.forEach(
                    repository -> assertThat(repository.committedState().revision()).isEqualTo(2L)));

            List<BrokerClusterAdapter> followers = repositories.stream()
                    .filter(repository -> repository != leader)
                    .toList();
            followers.get(0).close();
            followers.get(1).close();
            repositories.removeAll(followers);

            try {
                BrokerClusterConsensusResult minorityResult = leader.submitChange(
                                BrokerClusterCommandFixture.commitOperation(BrokerClusterCommandFixture.topology(
                                        "operation-2", "digest-2", 2L, "ADD_BROKER", "broker-e",
                                        List.of(active("broker-a"), active("broker-b"),
                                                active("broker-c"), discovered("broker-d"),
                                                discovered("broker-e")), 2_000L)))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertThat(minorityResult.status())
                        .isIn(BrokerClusterConsensusStatus.NOT_LEADER,
                                BrokerClusterConsensusStatus.UNAVAILABLE);
            } catch (CompletionException exception) {
                assertThat(exception.getCause()).isInstanceOf(java.util.concurrent.TimeoutException.class);
            } catch (java.util.concurrent.ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(java.util.concurrent.TimeoutException.class);
            }
            assertThat(leader.committedState().revision()).isEqualTo(2L);
            assertThat(leader.findOperation(new ClusterOperationId("operation-2"))).isEmpty();
        } finally {
            for (BrokerClusterAdapter repository : repositories) {
                repository.close();
            }
        }
    }

    @Test
    void learnerCatchesUpAndCanBePromotedDemotedAndRemoved() throws Exception {
        List<Integer> voterPorts = List.of(availablePort(), availablePort(), availablePort());
        int learnerPort = availablePort();
        List<String> voters = voterPorts.stream().map(this::endpoint).toList();
        String learnerEndpoint = endpoint(learnerPort);
        BrokerClusterState initialState = state(0L, 0L,
                active("broker-a"), active("broker-b"), active("broker-c"));
        List<BrokerClusterAdapter> repositories = new ArrayList<>();
        try {
            for (int index = 0; index < voterPorts.size(); index++) {
                repositories.add(createAdapter(
                        properties("broker-" + index, voterPorts.get(index),
                                temporaryDirectory.resolve("membership-voter-" + index), voters),
                        initialState));
            }
            BrokerRaftProperties learnerProperties = properties("broker-learner", learnerPort,
                    temporaryDirectory.resolve("membership-learner"), voters);
            learnerProperties.setInitialLearners(List.of(learnerEndpoint));
            learnerProperties.setBootstrapBrokers(List.of());
            BrokerClusterAdapter learner =
                    createAdapter(learnerProperties, initialState);
            repositories.add(learner);

            BrokerClusterAdapter leader = awaitLeader(repositories);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(leader.addLearner(new MembershipEndpoint(learnerEndpoint))
                            .toCompletableFuture().join().accepted()).isTrue());
            BrokerClusterConsensusResult committed = submitTopologyChange(leader,
                    BrokerClusterCommandFixture.topology(
                            "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-d",
                            List.of(active("broker-a"), active("broker-b"), active("broker-c"),
                                    discovered("broker-d")), 1_000L));
            assertThat(committed.accepted()).isTrue();
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(learner.committedState().revision()).isEqualTo(2L));

            long quorumReadIndex = leader.confirmLeadership(new QuorumReadNonce("learner-catch-up-proof"))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).readIndex().value();
            long learnerAppliedIndex = learner.getLocalBroker().fetchQuorumReadIndex().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(learnerAppliedIndex).isGreaterThanOrEqualTo(quorumReadIndex);

            assertThat(leader.promoteLearner(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().accepted())
                    .isTrue();
            assertThat(leader.consensusMembership().voters()).contains(learnerEndpoint);
            assertThat(leader.demoteVoter(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().accepted())
                    .isTrue();
            assertThat(leader.consensusMembership().learners()).contains(learnerEndpoint);
            assertThat(leader.removeMember(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().accepted())
                    .isTrue();
            assertThat(leader.consensusMembership().voters()).doesNotContain(learnerEndpoint);
            assertThat(leader.consensusMembership().learners()).doesNotContain(learnerEndpoint);
        } finally {
            for (BrokerClusterAdapter repository : repositories) {
                repository.close();
            }
        }
    }

    @Test
    void newLeaderResumesCommittedOperationWithoutPhaseRegression() throws Exception {
        List<Integer> ports = List.of(availablePort(), availablePort(), availablePort());
        List<String> voters = ports.stream().map(this::endpoint).toList();
        BrokerClusterState initialState = state(0L, 0L,
                active("broker-a"), active("broker-b"), active("broker-c"));
        List<BrokerClusterAdapter> repositories = new ArrayList<>();
        try {
            for (int index = 0; index < ports.size(); index++) {
                repositories.add(createAdapter(
                        properties("broker-" + index, ports.get(index),
                                temporaryDirectory.resolve("leader-resume-" + index), voters),
                        initialState));
            }
            BrokerClusterAdapter firstLeader = awaitLeader(repositories);
            BrokerClusterConsensusResult committed = submitTopologyChange(firstLeader,
                    BrokerClusterCommandFixture.topology(
                                    "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-d",
                                    List.of(active("broker-a"), active("broker-b"),
                                            active("broker-c"), discovered("broker-d")), 1_000L));
            assertThat(committed.operation().phase())
                    .isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> repositories.forEach(
                    repository -> assertThat(repository.findOperation(new ClusterOperationId("operation-1"))).isPresent()));

            firstLeader.close();
            repositories.remove(firstLeader);
            BrokerClusterAdapter secondLeader = awaitLeader(repositories);
            BrokerClusterConsensusCommand advance = BrokerClusterCommandFixture.advanceOperation(
                    "operation-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                    BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 2_000L);
            BrokerClusterConsensusResult advanced = secondLeader.submitChange(advance)
                    .toCompletableFuture().join();
            BrokerClusterConsensusResult replayed = secondLeader.submitChange(advance)
                    .toCompletableFuture().join();

            assertThat(advanced.status()).isEqualTo(BrokerClusterConsensusStatus.APPLIED);
            assertThat(advanced.operation().phase())
                    .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
            assertThat(replayed.status()).isEqualTo(BrokerClusterConsensusStatus.DUPLICATE);
            assertThat(replayed.operation().phase())
                    .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
        } finally {
            for (BrokerClusterAdapter repository : repositories) {
                repository.close();
            }
        }
    }

    @Test
    void appliesClusterStateAndOperationAtomicallyAndDeduplicatesRetries() {
        BrokerClusterState initialState = state(0L, 0L, active("broker-a"));
        RaftBrokerClusterStateMachine stateMachine = new RaftBrokerClusterStateMachine("cluster-a", initialState);
        BrokerClusterOperationProposal command = BrokerClusterCommandFixture.topology(
                "operation-1",
                "digest-1",
                0L,
                "ADD_BROKER",
                "broker-b",
                List.of(active("broker-a"), discovered("broker-b")),
                1_000L);

        BrokerClusterConsensusResult applied = applyTopologyChange(stateMachine, command);
        BrokerClusterConsensusResult duplicate = stateMachine.apply(
                BrokerClusterCommandFixture.commitOperation(command));

        assertThat(applied.status()).isEqualTo(BrokerClusterConsensusStatus.APPLIED);
        BrokerClusterState committed = stateMachine.committedState();
        assertThat(committed.revision()).isEqualTo(2L);
        assertThat(applied.operation().phase()).isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);
        assertThat(duplicate.status()).isEqualTo(BrokerClusterConsensusStatus.DUPLICATE);
        assertThat(stateMachine.committedState()).isEqualTo(committed);
        assertThat(duplicate.operation()).isEqualTo(applied.operation());
        assertThat(stateMachine.snapshot().operations()).containsOnly(applied.operation());
    }

    @Test
    void rejectsOperationIdReuseWithDifferentRequestAndStaleRevision() {
        BrokerClusterState initialState = state(0L, 0L, active("broker-a"));
        RaftBrokerClusterStateMachine stateMachine = new RaftBrokerClusterStateMachine("cluster-a", initialState);
        BrokerClusterOperationProposal accepted = BrokerClusterCommandFixture.topology(
                "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                List.of(active("broker-a"), discovered("broker-b")), 1_000L);
        applyTopologyChange(stateMachine, accepted);

        BrokerClusterConsensusResult reused = stateMachine.apply(BrokerClusterCommandFixture.beginOperation(
                BrokerClusterCommandFixture.topology(
                "operation-1", "different", 2L, "REMOVE_BROKER", "broker-b",
                List.of(active("broker-a")), 2_000L)));
        BrokerClusterConsensusResult stale = stateMachine.apply(BrokerClusterCommandFixture.beginOperation(
                BrokerClusterCommandFixture.topology(
                "operation-2", "digest-2", 0L, "ADD_BROKER", "broker-c",
                List.of(active("broker-a"), discovered("broker-b"), discovered("broker-c")),
                2_000L)));

        assertThat(reused.status()).isEqualTo(BrokerClusterConsensusStatus.REJECTED);
        assertThat(reused.message()).contains("operationId");
        assertThat(stale.status()).isEqualTo(BrokerClusterConsensusStatus.CONFLICT);
        assertThat(stateMachine.committedState().revision()).isEqualTo(2L);
        assertThat(stateMachine.snapshot().operations()).hasSize(1);
    }

    @Test
    void restoresStateAndOperationsFromSnapshotWithoutRegressingPhase() {
        BrokerClusterState initialState = state(0L, 0L, active("broker-a"));
        RaftBrokerClusterStateMachine first = new RaftBrokerClusterStateMachine("cluster-a", initialState);
        applyTopologyChange(first, BrokerClusterCommandFixture.topology(
                "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                List.of(active("broker-a"), discovered("broker-b")), 1_000L));
        first.apply(BrokerClusterCommandFixture.advanceOperation(
                "operation-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 2_000L));

        byte[] encoded = BrokerClusterSnapshotCodec.encode(first.snapshot());
        RaftBrokerClusterStateMachine recovered = new RaftBrokerClusterStateMachine("cluster-a", initialState);
        recovered.restore(BrokerClusterSnapshotCodec.decode(encoded));
        BrokerClusterConsensusResult delayed = recovered.apply(BrokerClusterCommandFixture.advanceOperation(
                "operation-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 2_000L));

        assertThat(recovered.committedState()).isEqualTo(first.committedState());
        assertThat(recovered.findOperation(new ClusterOperationId("operation-1"))).contains(first.findOperation(new ClusterOperationId("operation-1")).orElseThrow());
        assertThat(delayed.status()).isEqualTo(BrokerClusterConsensusStatus.DUPLICATE);
        assertThat(delayed.operation().phase())
                .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
    }

    @Test
    void persistsDrainEvidenceAndCompletesBrokerRemovalAtomically() {
        BrokerClusterState initialState = state(0L, 4L,
                active("broker-a"), active("broker-b"));
        RaftBrokerClusterStateMachine first = new RaftBrokerClusterStateMachine("cluster-a", initialState);
        DrainProgress frozen = new DrainProgress(List.of(
                checkpoint("gateway-a", 900L)),
                Instant.ofEpochMilli(2_000L), null);
        BrokerClusterOperationProposal start = BrokerClusterCommandFixture.topology(
                "drain-1", "digest-1", 0L, "DRAIN_BROKER", "broker-b",
                List.of(active("broker-a"), draining("broker-b")), 1_000L, frozen);

        applyTopologyChange(first, start);
        first.apply(BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 1_100L));
        first.apply(BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                BrokerClusterOperationPhase.REBUILDING_ROUTES, null, 1_200L));
        first.apply(BrokerClusterCommandFixture.confirmDrainGateway(
                "drain-1", "digest-1", "gateway-a", 12L, 3L, "checksum-a", 1_300L));
        first.apply(BrokerClusterCommandFixture.summarizeDrain(
                "drain-1", "digest-1", summary(3L, 2L, 0L, true),
                2_000L));

        byte[] encoded = BrokerClusterSnapshotCodec.encode(first.snapshot());
        RaftBrokerClusterStateMachine recovered = new RaftBrokerClusterStateMachine("cluster-a", initialState);
        recovered.restore(BrokerClusterSnapshotCodec.decode(encoded));
        assertThat(new String(encoded, StandardCharsets.UTF_8))
                .contains("\"recoveryEvidence\"")
                .doesNotContain("\"routeSummary\"");
        assertThat(recovered.findOperation(new ClusterOperationId("drain-1")).orElseThrow()
                .drainProgress().recoveryEvidence()).isEqualTo(summary(3L, 2L, 0L, true));
        BrokerClusterConsensusResult completed = recovered.apply(
                BrokerClusterCommandFixture.completeDrain(
                        "drain-1", "digest-1", recovered.committedState().revision(), 2_000L));

        assertThat(completed.status()).isEqualTo(BrokerClusterConsensusStatus.APPLIED);
        assertThat(completed.operation().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(completed.operation().drainProgress().gateways().getFirst()
                .confirmedRouteRevision().value()).isEqualTo(12L);
        assertThat(recovered.committedState().brokers()).filteredOn(
                        broker -> broker.brokerId().equals("broker-b"))
                .extracting(BrokerNode::routingState)
                .containsExactly(BrokerRoutingState.INACTIVE);
    }

    @Test
    void boundsOperationHistoryWithoutEvictingInFlightOperation() {
        BrokerClusterState initialState = state(0L, 0L, active("broker-a"));
        RaftBrokerClusterStateMachine stateMachine =
                new RaftBrokerClusterStateMachine("cluster-a", initialState, 1);
        applyTopologyChange(stateMachine, BrokerClusterCommandFixture.topology(
                "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                List.of(active("broker-a"), discovered("broker-b")), 1_000L));

        BrokerClusterConsensusResult full = applyTopologyChange(stateMachine,
                BrokerClusterCommandFixture.topology(
                "operation-2", "digest-2", 2L, "ADD_BROKER", "broker-c",
                List.of(active("broker-a"), discovered("broker-b"), discovered("broker-c")),
                2_000L));
        assertThat(full.status()).isEqualTo(BrokerClusterConsensusStatus.UNAVAILABLE);

        stateMachine.apply(BrokerClusterCommandFixture.advanceOperation(
                "operation-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.FAILED, "test failure", 3_000L));
        BrokerClusterConsensusResult applied = applyTopologyChange(stateMachine,
                BrokerClusterCommandFixture.topology(
                "operation-2", "digest-2", 2L, "ADD_BROKER", "broker-c",
                List.of(active("broker-a"), discovered("broker-b"), discovered("broker-c")),
                4_000L));

        assertThat(applied.status()).isEqualTo(BrokerClusterConsensusStatus.APPLIED);
        assertThat(stateMachine.findOperation(new ClusterOperationId("operation-1"))).isEmpty();
        assertThat(stateMachine.findOperation(new ClusterOperationId("operation-2"))).isPresent();
    }

    private BrokerClusterState state(long revision, long epoch, BrokerNode... brokers) {
        return new BrokerClusterState(revision, epoch,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(brokers));
    }

    private BrokerClusterConsensusResult applyTopologyChange(
            RaftBrokerClusterStateMachine stateMachine,
            BrokerClusterOperationProposal command) {
        BrokerClusterConsensusResult begun = stateMachine.apply(
                BrokerClusterCommandFixture.beginOperation(command));
        if (!begun.accepted()) {
            return begun;
        }
        if (begun.operation().phase() == BrokerClusterOperationPhase.FENCING_OLD_EPOCH) {
            stateMachine.apply(BrokerClusterCommandFixture.fenceEpoch(fenceFor(
                    stateMachine.committedState(), command.operationId().value())));
        }
        return stateMachine.apply(BrokerClusterCommandFixture.commitOperation(command));
    }

    private BrokerClusterConsensusResult submitTopologyChange(
            BrokerClusterAdapter repository,
            BrokerClusterOperationProposal command) {
        BrokerClusterConsensusResult begun = repository.submitChange(
                BrokerClusterCommandFixture.beginOperation(command)).toCompletableFuture().join();
        if (!begun.accepted()) {
            return begun;
        }
        if (begun.operation().phase() == BrokerClusterOperationPhase.FENCING_OLD_EPOCH) {
            repository.submitChange(BrokerClusterCommandFixture.fenceEpoch(fenceFor(
                    repository.committedState(), command.operationId().value()))).toCompletableFuture().join();
        }
        return repository.submitChange(BrokerClusterCommandFixture.commitOperation(command))
                .toCompletableFuture().join();
    }

    private RoutingEpochFence fenceFor(BrokerClusterState state, String operationId) {
        return RoutingEpochFence.builder()
                .operationId(new ClusterOperationId(
                        operationId))
                .epoch(new RoutingEpoch(state.epoch()))
                .leaderTerm(term(1L))
                .quorumReadIndex(readIndex(1L))
                .fencedBrokerIds(state.activeBrokers().stream().map(BrokerNode::brokerId)
                        .map(BrokerId::new).toList())
                .build();
    }

    private BrokerNode active(String brokerId) {
        return new BrokerNode(brokerId, brokerId + ".example:19000",
                BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE);
    }

    private BrokerNode discovered(String brokerId) {
        return new BrokerNode(brokerId, brokerId + ".example:19000",
                BrokerMembershipRole.DISCOVERED, BrokerRoutingState.INACTIVE);
    }

    private BrokerNode draining(String brokerId) {
        return new BrokerNode(brokerId, brokerId + ".example:19000",
                BrokerMembershipRole.VOTER, BrokerRoutingState.DRAINING);
    }

    private BrokerClusterAdapter createAdapter(BrokerRaftProperties properties, BrokerClusterState state)
            throws IOException {
        return new BrokerClusterAdapter(properties, state, mock(BoltInvoker.class), new BrokerProperties(), Runnable::run,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), new BrokerRoutingProperties(), Runnable::run,
                Runnable::run, mock(GatewayClient.class), new MetricsCollector(new SimpleMeterRegistry()));
    }

    private BrokerRaftProperties properties(String nodeId, int port, Path dataDirectory,
                                            List<String> voters) {
        BrokerRaftProperties properties = new BrokerRaftProperties();
        properties.setClusterId("cluster-a");
        properties.setNodeId(nodeId);
        properties.setEndpoint(endpoint(port));
        properties.setDataDirectory(dataDirectory.toString());
        properties.setInitialVoters(voters);
        List<BrokerRaftProperties.BootstrapBroker> bootstrapBrokers =
                java.util.stream.IntStream.range(0, voters.size()).mapToObj(index -> {
                    BrokerRaftProperties.BootstrapBroker broker =
                            new BrokerRaftProperties.BootstrapBroker();
                    broker.setBrokerId("broker-" + (char) ('a' + index));
                    broker.setRouteEndpoint(voters.get(index));
                    broker.setMembershipEndpoint(voters.get(index));
                    return broker;
                })
                .toList();
        properties.setBootstrapBrokers(bootstrapBrokers);
        properties.setElectionTimeoutMillis(300);
        properties.setSnapshotIntervalSeconds(0);
        return properties;
    }

    private String endpoint(int port) {
        return "127.0.0.1:" + port;
    }

    private int availablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private BrokerClusterAdapter awaitLeader(
            List<BrokerClusterAdapter> repositories) {
        java.util.concurrent.atomic.AtomicReference<BrokerClusterAdapter> leader =
                new java.util.concurrent.atomic.AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            List<BrokerClusterAdapter> leaders = repositories.stream()
                    .filter(adapter -> adapter.getLocalBroker().isLeader())
                    .toList();
            if (leaders.size() != 1) {
                return false;
            }
            leader.set(leaders.get(0));
            return true;
        });
        return leader.get();
    }
}
