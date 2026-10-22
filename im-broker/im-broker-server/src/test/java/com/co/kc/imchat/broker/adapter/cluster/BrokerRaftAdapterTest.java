package com.co.kc.imchat.broker.adapter.cluster;

import java.util.OptionalLong;
import java.util.Map;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.raft.core.RaftRuntime;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.plugin.raft.model.RaftResult;
import java.util.Optional;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
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
import com.co.kc.imchat.common.domain.user.model.UserId;
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
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.result.QuorumReadIndexResult;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
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
import java.util.Set;
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

class BrokerRaftAdapterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void namedOperationsSubmitThroughTheOwnedRuntimeAndLocalViewReadsLiveRole() throws Exception {
        List<BrokerClusterConsensusCommand> submitted = new ArrayList<>();
        try (MockedConstruction<RaftRuntime> runtimes =
                     Mockito.mockConstruction(RaftRuntime.class,
                             (runtime, context) -> {
                                 when(runtime.nodeId()).thenReturn("broker-a");
                                 when(runtime.isLeader()).thenReturn(false, true, false);
                                 when(runtime.localLeaderTerm()).thenReturn(OptionalLong.empty(),
                                         OptionalLong.of(7L), OptionalLong.of(8L));
                                 when(runtime.submit(any())).thenAnswer(invocation -> {
                                     submitted.add(invocation.getArgument(0));
                                     return CompletableFuture.completedFuture(new RaftResult<>(
                                             RaftResult.Status.APPLIED,
                                             BrokerClusterConsensusResult.applied(), null, null));
                                 });
                             });
             BrokerRaftAdapter adapter = createAdapter(properties("broker-a", 18021,
                     temporaryDirectory.resolve("named-operations"), List.of(endpoint(18021))),
                     state(0L, 0L, active("broker-a")))) {
            BrokerRaftAdapter.LocalBroker local = adapter.getLocalBroker();
            assertThat(adapter.getLocalBroker()).isSameAs(local);
            assertThat(local.getId()).isEqualTo(new BrokerId("broker-a"));
            assertThat(adapter.getClusterId()).isEqualTo(
                    new com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId("cluster-a"));
            assertThat(local.isLeader()).isFalse();
            assertThat(local.isLeader()).isTrue();
            assertThat(local.isLeader()).isFalse();
            assertThat(local.getLeaderTerm()).isEmpty();
            assertThat(local.getLeaderTerm()).contains(new LeaderTerm(7L));
            assertThat(local.getLeaderTerm()).contains(new LeaderTerm(8L));
            assertThat(runtimes.constructed()).hasSize(1);

            BrokerClusterOperation operation = adapter.getClusterSnapshot().submitOperation(
                    BrokerClusterCommandFixture.topology("named", "digest", 0L, "ADD_BROKER", "broker-b",
                            List.of(active("broker-a"), discovered("broker-b")), 1_000L)).operation();
            Instant changedAt = Instant.ofEpochMilli(2_000L);
            ClusterRevision revision =
                    new ClusterRevision(42L);
            OperationFailureReason reason =
                    new OperationFailureReason("rejected");
            long before = Instant.now().toEpochMilli();
            adapter.beginOperation(operation).toCompletableFuture().join();
            adapter.commitOperation(operation).toCompletableFuture().join();
            adapter.failMembershipOperation(operation,
                    new BrokerConsensusMembershipResult(
                            BrokerConsensusMembershipResult.Status.REJECTED,
                            null, "rejected")).toCompletableFuture().join();
            adapter.completeMembershipOperation(operation).toCompletableFuture().join();
            com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate confirmation =
                    com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate.confirmGateway(
                            new GatewayId("gateway-a"), new com.co.kc.imchat.broker.domain.connection.model.RouteRevision(7L),
                            new RouteCount(3L), new com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest("a".repeat(64)));
            adapter.confirmGatewayRoutes(operation, confirmation).toCompletableFuture().join();
            adapter.failDrain(operation, reason, changedAt).toCompletableFuture().join();
            adapter.completeDrain(operation, revision).toCompletableFuture().join();
            adapter.forceDrain(operation, revision, reason).toCompletableFuture().join();
            adapter.beginEpochAuthorization(operation).toCompletableFuture().join();
            adapter.completeEpochAuthorization(operation).toCompletableFuture().join();
            adapter.saveRecoveryEvidence(operation, summary(0, 0, 0, true), changedAt).toCompletableFuture().join();
            adapter.verifyRouteRecovery(operation, Duration.ofSeconds(10), changedAt).toCompletableFuture().join();
            adapter.completeRouteRecovery(operation).toCompletableFuture().join();

            BeginBrokerClusterOperationCommand actualBegin =
                    (BeginBrokerClusterOperationCommand) submitted.get(0);
            assertThat(actualBegin.proposal().operationId()).isEqualTo(operation.id());
            assertThat(actualBegin.proposal().commandDigest()).isEqualTo(operation.commandDigest());
            assertThat(actualBegin.proposal().expectedRevision()).isEqualTo(operation.acceptedRevision());
            assertThat(actualBegin.proposal().operationType()).isEqualTo(operation.type());
            assertThat(actualBegin.proposal().targetBrokerId()).isEqualTo(operation.targetBrokerId());
            assertThat(actualBegin.proposal().changedAt()).isEqualTo(operation.createdAt());
            assertThat(actualBegin.proposal().initialDrainProgress()).isEqualTo(operation.drainProgress());
            assertThat(actualBegin.proposal().membershipEndpoint()).isEqualTo(operation.membershipEndpoint());
            assertThat(actualBegin.proposal().routeEndpoint()).isEqualTo(operation.routeEndpoint());
            assertThat(actualBegin.proposal().minimumVoterCount()).isEqualTo(operation.minimumVoterCount());
            BeginBrokerClusterOperationCommand decodedBegin = JsonUtils.getMapper().readValue(
                    JsonUtils.getMapper().writeValueAsBytes(actualBegin), BeginBrokerClusterOperationCommand.class);
            assertThat(decodedBegin).isEqualTo(actualBegin);

            long after = Instant.now().toEpochMilli();
            BrokerClusterRaftTransformer mapper =
                    BrokerClusterRaftTransformer.INSTANCE;
            List<BrokerClusterConsensusCommand> expected = List.of(
                    mapper.beginCommandFrom(operation),
                    mapper.commitCommandFrom(operation, changedAt),
                    mapper.operationPhaseCommandFrom(operation, BrokerClusterOperationPhase.PENDING,
                            BrokerClusterOperationPhase.FAILED, reason, changedAt),
                    mapper.operationPhaseCommandFrom(operation, BrokerClusterOperationPhase.RAFT_COMMITTED,
                            BrokerClusterOperationPhase.SUCCEEDED, null, changedAt),
                    mapper.gatewayConfirmationCommandFrom(operation, confirmation, changedAt),
                    mapper.drainFailureCommandFrom(operation, reason, changedAt),
                    mapper.drainCompletionCommandFrom(operation, revision, changedAt),
                    mapper.forcedDrainCommandFrom(operation, revision, reason, changedAt),
                    mapper.operationPhaseCommandFrom(operation, BrokerClusterOperationPhase.RAFT_COMMITTED,
                            BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, changedAt),
                    mapper.operationPhaseCommandFrom(operation, BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                            BrokerClusterOperationPhase.REBUILDING_ROUTES, null, changedAt),
                    mapper.recoveryEvidenceCommandFrom(operation, summary(0, 0, 0, true), changedAt),
                    mapper.recoveryVerificationCommandFrom(operation, Duration.ofSeconds(10), changedAt),
                    mapper.recoveryCompletionCommandFrom(operation, changedAt));
            assertThat(submitted).hasSameSizeAs(expected);
            for (int index = 0; index < expected.size(); index++) {
                assertThat(submitted.get(index)).isInstanceOf(expected.get(index).getClass());
                com.fasterxml.jackson.databind.node.ObjectNode actualPayload = JsonUtils.getMapper().valueToTree(submitted.get(index));
                com.fasterxml.jackson.databind.node.ObjectNode expectedPayload = JsonUtils.getMapper().valueToTree(expected.get(index));
                if (index == 12) {
                    assertThat(((com.co.kc.imchat.broker.model.cqrs.command.CompleteRouteRecoveryCommand)
                            submitted.get(index)).changedAt().toEpochMilli()).isBetween(before, after);
                    expectedPayload.set("changedAt", actualPayload.get("changedAt"));
                } else if (List.of(1, 2, 3, 4, 6, 7, 8, 9).contains(index)) {
                    assertThat(JsonUtils.getMapper().convertValue(actualPayload.get("changedAt"), Instant.class).toEpochMilli()).isBetween(before, after);
                    expectedPayload.set("changedAt", actualPayload.get("changedAt"));
                }
                assertThat(actualPayload).isEqualTo(expectedPayload);
            }
        }
    }

    @Test
    void testCommandDispatchRejectsAlteredPayloadBeforeSubmitting() {
        RaftBrokerClusterStateMachine stateMachine = new RaftBrokerClusterStateMachine("cluster-a",
                state(0L, 0L, active("broker-a")), 1000, Map.of());
        BrokerClusterOperation operation = stateMachine.getCluster().submitOperation(
                BrokerClusterCommandFixture.topology("named", "digest", 0L, "ADD_BROKER", "broker-b",
                        List.of(active("broker-a"), discovered("broker-b")), 1_000L)).operation();
        Instant changedAt = Instant.ofEpochMilli(2_000L);
        BrokerRaftAdapter commandAdapter = mock(BrokerRaftAdapter.class);
        when(commandAdapter.findOperation(operation.id())).thenReturn(Optional.of(operation));
        assertThatThrownBy(() -> BrokerRaftTestBindings.submit(commandAdapter,
                new CommitBrokerOperationCommand(
                        operation.id(), "wrong-digest", operation.acceptedRevision(), changedAt)))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> BrokerRaftTestBindings.submit(commandAdapter,
                BrokerClusterCommandFixture.advanceOperation(operation.id().value(), "wrong-digest",
                        BrokerClusterOperationPhase.RAFT_COMMITTED, BrokerClusterOperationPhase.SUCCEEDED,
                        null, changedAt.toEpochMilli())))
                .isInstanceOf(AssertionError.class);
        verify(commandAdapter, Mockito.never()).commitOperation(any());
        verify(commandAdapter, Mockito.never()).completeMembershipOperation(any());
    }

    @Test
    void invalidCompletionTimeDoesNotPreventLaterRaftCommands() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("completion-time"), List.of(endpoint(port)));
        try (BrokerRaftAdapter adapter = createAdapter(properties,
                state(0L, 0L, active("broker-a")))) {
            await().atMost(Duration.ofSeconds(10)).until(adapter.getLocalBroker()::isLeader);
            assertThat(adapter.getLocalBroker().getLeaderTerm()).contains(
                    adapter.confirmLeadership(new QuorumReadNonce("local-term-wiring"))
                            .toCompletableFuture().join().leaderTerm());
            assertThat(adapter.getLocalBroker().getId()).isEqualTo(new BrokerId("broker-a"));
            assertThat(adapter.getLeaderEndpoint()).isEqualTo(endpoint(port));
            assertThat(adapter.fetchQuorumReadIndex().toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isGreaterThanOrEqualTo(0L);
            assertThat(submitTopologyChange(adapter, BrokerClusterCommandFixture.topology(
                    "join", "digest", 0L, "ADD_BROKER", "broker-b",
                    List.of(active("broker-a"), discovered("broker-b")), 1_000L)).accepted()).isTrue();

            BrokerClusterConsensusResult rejected = BrokerRaftTestBindings.submit(adapter, BrokerClusterCommandFixture.advanceOperation(
                    "join", "digest", BrokerClusterOperationPhase.RAFT_COMMITTED,
                    BrokerClusterOperationPhase.FAILED, "invalid-time", 999L))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(rejected.status()).isEqualTo(Status.REJECTED);
            assertThat(adapter.findOperation(new ClusterOperationId("join")).orElseThrow().phase())
                    .isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);

            BrokerClusterConsensusResult completed = BrokerRaftTestBindings.submit(adapter, BrokerClusterCommandFixture.advanceOperation(
                    "join", "digest", BrokerClusterOperationPhase.RAFT_COMMITTED,
                    BrokerClusterOperationPhase.SUCCEEDED, null, 1_001L))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(completed.status()).isEqualTo(Status.APPLIED);
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

        try (BrokerRaftAdapter repository =
                     createAdapter(properties, initialState)) {
            await().atMost(Duration.ofSeconds(10)).until(repository.getLocalBroker()::isLeader);
            BrokerClusterConsensusResult result = submitTopologyChange(repository,
                    BrokerClusterCommandFixture.topology(
                            "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                            List.of(active("broker-a"), discovered("broker-b")), 1_000L));

            assertThat(result.status()).isEqualTo(Status.APPLIED);
            assertThat(repository.createSnapshot().toCompletableFuture().join().applied()).isTrue();
        }

        try (BrokerRaftAdapter recovered =
                     createAdapter(properties, initialState)) {
            await().atMost(Duration.ofSeconds(10)).until(recovered.getLocalBroker()::isLeader);
            assertThat(recovered.getCommittedState().revision()).isEqualTo(2L);
            assertThat(recovered.findOperation(new ClusterOperationId("operation-1"))).isPresent();
            BrokerClusterConsensusResult duplicate = BrokerRaftTestBindings.submit(recovered, BrokerClusterCommandFixture.commitOperation(
                    BrokerClusterCommandFixture.topology(
                            "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                            List.of(active("broker-a"), discovered("broker-b")), 1_000L)))
                    .toCompletableFuture().join();
            assertThat(duplicate.status()).isEqualTo(Status.DUPLICATE);
        }
    }

    @Test
    void recoveryCommandsValidateTimeoutAndRestoreDegradedIntentAfterRestart() throws Exception {
        int port = availablePort();
        BrokerRaftProperties properties = properties("broker-a", port,
                temporaryDirectory.resolve("recovery"), List.of(endpoint(port)));
        BrokerClusterState initialState = state(0L, 4L, active("broker-a"), active("broker-b"));
        ClusterOperationId operationId = new ClusterOperationId("failure-1");
        try (BrokerRaftAdapter adapter = createAdapter(properties, initialState)) {
            await().atMost(Duration.ofSeconds(10)).until(adapter.getLocalBroker()::isLeader);
            DrainProgress progress = new DrainProgress(List.of(checkpoint("gateway-a", 900L)),
                    Instant.ofEpochMilli(2_000L), null);
            assertThat(submitTopologyChange(adapter, BrokerClusterCommandFixture.topology(
                    "failure-1", "digest-1", 0L, "FAIL_BROKER", "broker-b",
                    List.of(active("broker-a"), new BrokerNode("broker-b", "broker-b.example:19000",
                            BrokerMembershipRole.VOTER, BrokerRoutingState.INACTIVE)), 1_000L, progress)).accepted()).isTrue();
            assertThat(BrokerRaftTestBindings.submit(adapter, BrokerClusterCommandFixture.advanceOperation(
                    "failure-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                    BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 1_100L))
                    .toCompletableFuture().join().accepted()).isTrue();
            assertThat(BrokerRaftTestBindings.submit(adapter, BrokerClusterCommandFixture.advanceOperation(
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
        try (BrokerRaftAdapter restored = createAdapter(properties, initialState)) {
            await().atMost(Duration.ofSeconds(10)).until(restored.getLocalBroker()::isLeader);
            BrokerClusterOperation operation = restored.findOperation(operationId).orElseThrow();
            assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.VERIFYING);
            BrokerClusterConsensusResult completed = restored.completeRouteRecovery(operation)
                    .toCompletableFuture().join();
            assertThat(completed.operation().phase()).isEqualTo(BrokerClusterOperationPhase.DEGRADED);
            assertThat(completed.operation().error()).isEqualTo(operation.error());
            assertThat(restored.completeRouteRecovery(operation)
                    .toCompletableFuture().join().status()).isEqualTo(Status.DUPLICATE);
        }
    }

    @Test
    void threeVotersReplicateButMinorityCannotCommit() throws Exception {
        List<Integer> ports = List.of(availablePort(), availablePort(), availablePort());
        List<String> voters = ports.stream().map(this::endpoint).toList();
        BrokerClusterState initialState = state(0L, 0L,
                active("broker-a"), active("broker-b"), active("broker-c"));
        List<BrokerRaftAdapter> repositories = new ArrayList<>();
        try {
            for (int index = 0; index < ports.size(); index++) {
                BrokerRaftProperties properties = properties("broker-" + index, ports.get(index),
                        temporaryDirectory.resolve("three-voter-" + index), voters);
                properties.setCommandTimeoutMs(800);
                repositories.add(createAdapter(properties, initialState));
            }
            BrokerRaftAdapter leader = awaitLeader(repositories);
            BrokerClusterConsensusResult committed = submitTopologyChange(leader,
                    BrokerClusterCommandFixture.topology(
                            "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-d",
                            List.of(active("broker-a"), active("broker-b"), active("broker-c"),
                                    discovered("broker-d")), 1_000L));
            assertThat(committed.status()).isEqualTo(Status.APPLIED);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> repositories.forEach(
                    repository -> assertThat(repository.getCommittedState().revision()).isEqualTo(2L)));

            List<BrokerRaftAdapter> followers = repositories.stream()
                    .filter(repository -> repository != leader)
                    .toList();
            followers.get(0).close();
            followers.get(1).close();
            repositories.removeAll(followers);

            try {
                BrokerClusterConsensusResult minorityResult = BrokerRaftTestBindings.submit(leader,
                                BrokerClusterCommandFixture.beginOperation(BrokerClusterCommandFixture.topology(
                                        "operation-2", "digest-2", 2L, "ADD_BROKER", "broker-e",
                                        List.of(active("broker-a"), active("broker-b"),
                                                active("broker-c"), discovered("broker-d"),
                                                discovered("broker-e")), 2_000L)))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertThat(minorityResult.status())
                        .isIn(Status.NOT_LEADER,
                                Status.UNAVAILABLE);
            } catch (CompletionException exception) {
                assertThat(exception.getCause()).isInstanceOf(java.util.concurrent.TimeoutException.class);
            } catch (java.util.concurrent.ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(java.util.concurrent.TimeoutException.class);
            }
            assertThat(leader.getCommittedState().revision()).isEqualTo(2L);
            assertThat(leader.findOperation(new ClusterOperationId("operation-2"))).isEmpty();
        } finally {
            for (BrokerRaftAdapter repository : repositories) {
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
        List<BrokerRaftAdapter> repositories = new ArrayList<>();
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
            BrokerRaftAdapter learner =
                    createAdapter(learnerProperties, initialState);
            repositories.add(learner);

            BrokerRaftAdapter leader = awaitLeader(repositories);
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
                    .untilAsserted(() -> assertThat(learner.getCommittedState().revision()).isEqualTo(2L));

            long quorumReadIndex = leader.confirmLeadership(new QuorumReadNonce("learner-catch-up-proof"))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).readIndex().value();
            long learnerAppliedIndex = learner.fetchQuorumReadIndex().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(learnerAppliedIndex).isGreaterThanOrEqualTo(quorumReadIndex);

            assertThat(leader.addLearner(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().status())
                    .isEqualTo(BrokerConsensusMembershipResult.Status.DUPLICATE);
            assertThat(leader.addLearner(new MembershipEndpoint(voters.getFirst())).toCompletableFuture().join().status())
                    .isEqualTo(BrokerConsensusMembershipResult.Status.REJECTED);
            Set<MembershipEndpoint> voterSnapshot = leader.getConsensusMembership().voters();
            Set<MembershipEndpoint> learnerSnapshot = leader.getConsensusMembership().learners();
            assertThat(voterSnapshot).containsExactlyInAnyOrderElementsOf(voters.stream().map(MembershipEndpoint::new).toList());
            assertThat(learnerSnapshot).containsExactly(new MembershipEndpoint(learnerEndpoint));
            assertThatThrownBy(() -> voterSnapshot.add(new MembershipEndpoint(learnerEndpoint)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(learnerSnapshot::clear)
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThat(leader.promoteLearner(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().accepted())
                    .isTrue();
            List<String> promotedVoters = new ArrayList<>(voters);
            promotedVoters.add(learnerEndpoint);
            assertThat(leader.getConsensusMembership().voters())
                    .containsExactlyInAnyOrderElementsOf(promotedVoters.stream().map(MembershipEndpoint::new).toList());
            assertThat(leader.getConsensusMembership().learners()).isEmpty();
            assertThat(leader.promoteLearner(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().status())
                    .isEqualTo(BrokerConsensusMembershipResult.Status.DUPLICATE);
            assertThat(voterSnapshot).containsExactlyInAnyOrderElementsOf(voters.stream().map(MembershipEndpoint::new).toList());
            assertThat(learnerSnapshot).containsExactly(new MembershipEndpoint(learnerEndpoint));
            assertThat(leader.demoteVoter(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().accepted())
                    .isTrue();
            assertThat(leader.getConsensusMembership().learners()).contains(new MembershipEndpoint(learnerEndpoint));
            assertThat(leader.demoteVoter(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().status())
                    .isEqualTo(BrokerConsensusMembershipResult.Status.DUPLICATE);
            assertThat(leader.removeMember(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().accepted())
                    .isTrue();
            assertThat(leader.getConsensusMembership().voters()).doesNotContain(new MembershipEndpoint(learnerEndpoint));
            assertThat(leader.getConsensusMembership().learners()).doesNotContain(new MembershipEndpoint(learnerEndpoint));
            assertThat(leader.removeMember(new MembershipEndpoint(learnerEndpoint)).toCompletableFuture().join().status())
                    .isEqualTo(BrokerConsensusMembershipResult.Status.DUPLICATE);
        } finally {
            for (BrokerRaftAdapter repository : repositories) {
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
        List<BrokerRaftAdapter> repositories = new ArrayList<>();
        try {
            for (int index = 0; index < ports.size(); index++) {
                repositories.add(createAdapter(
                        properties("broker-" + index, ports.get(index),
                                temporaryDirectory.resolve("leader-resume-" + index), voters),
                        initialState));
            }
            BrokerRaftAdapter firstLeader = awaitLeader(repositories);
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
            BrokerRaftAdapter secondLeader = awaitLeader(repositories);
            BrokerClusterConsensusCommand advance = BrokerClusterCommandFixture.advanceOperation(
                    "operation-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                    BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 2_000L);
            BrokerClusterConsensusResult advanced = BrokerRaftTestBindings.submit(secondLeader, advance)
                    .toCompletableFuture().join();
            BrokerClusterConsensusResult replayed = BrokerRaftTestBindings.submit(secondLeader, advance)
                    .toCompletableFuture().join();

            assertThat(advanced.status()).isEqualTo(Status.APPLIED);
            assertThat(advanced.operation().phase())
                    .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
            assertThat(replayed.status()).isEqualTo(Status.DUPLICATE);
            assertThat(replayed.operation().phase())
                    .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
        } finally {
            for (BrokerRaftAdapter repository : repositories) {
                repository.close();
            }
        }
    }

    @Test
    void appliesClusterStateAndOperationAtomicallyAndDeduplicatesRetries() {
        BrokerClusterState initialState = state(0L, 0L, active("broker-a"));
        RaftBrokerClusterStateMachine stateMachine = new RaftBrokerClusterStateMachine("cluster-a", initialState, 1000, Map.of());
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

        assertThat(applied.status()).isEqualTo(Status.APPLIED);
        BrokerClusterState committed = stateMachine.committedState();
        assertThat(committed.revision()).isEqualTo(2L);
        assertThat(applied.operation().phase()).isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);
        assertThat(duplicate.status()).isEqualTo(Status.DUPLICATE);
        assertThat(stateMachine.committedState()).isEqualTo(committed);
        assertThat(duplicate.operation()).isEqualTo(applied.operation());
        assertThat(stateMachine.snapshotState().operations()).containsOnly(applied.operation());
    }

    @Test
    void rejectsOperationIdReuseWithDifferentRequestAndStaleRevision() {
        BrokerClusterState initialState = state(0L, 0L, active("broker-a"));
        RaftBrokerClusterStateMachine stateMachine = new RaftBrokerClusterStateMachine("cluster-a", initialState, 1000, Map.of());
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

        assertThat(reused.status()).isEqualTo(Status.REJECTED);
        assertThat(reused.message()).contains("operationId");
        assertThat(stale.status()).isEqualTo(Status.CONFLICT);
        assertThat(stateMachine.committedState().revision()).isEqualTo(2L);
        assertThat(stateMachine.snapshotState().operations()).hasSize(1);
    }

    @Test
    void restoresStateAndOperationsFromSnapshotWithoutRegressingPhase() {
        BrokerClusterState initialState = state(0L, 0L, active("broker-a"));
        RaftBrokerClusterStateMachine first = new RaftBrokerClusterStateMachine("cluster-a", initialState, 1000, Map.of());
        applyTopologyChange(first, BrokerClusterCommandFixture.topology(
                "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                List.of(active("broker-a"), discovered("broker-b")), 1_000L));
        first.apply(BrokerClusterCommandFixture.advanceOperation(
                "operation-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 2_000L));

        byte[] encoded = BrokerClusterSnapshot.encode(first.snapshotState());
        RaftBrokerClusterStateMachine recovered = new RaftBrokerClusterStateMachine("cluster-a", initialState, 1000, Map.of());
        recovered.restore(BrokerClusterSnapshot.decode(encoded));
        BrokerClusterConsensusResult delayed = recovered.apply(BrokerClusterCommandFixture.advanceOperation(
                "operation-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 2_000L));

        assertThat(recovered.committedState()).isEqualTo(first.committedState());
        assertThat(recovered.findOperation(new ClusterOperationId("operation-1"))).contains(first.findOperation(new ClusterOperationId("operation-1")).orElseThrow());
        assertThat(delayed.status()).isEqualTo(Status.DUPLICATE);
        assertThat(delayed.operation().phase())
                .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
    }

    @Test
    void persistsDrainEvidenceAndCompletesBrokerRemovalAtomically() {
        BrokerClusterState initialState = state(0L, 4L,
                active("broker-a"), active("broker-b"));
        RaftBrokerClusterStateMachine first = new RaftBrokerClusterStateMachine("cluster-a", initialState, 1000, Map.of());
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

        byte[] encoded = BrokerClusterSnapshot.encode(first.snapshotState());
        RaftBrokerClusterStateMachine recovered = new RaftBrokerClusterStateMachine("cluster-a", initialState, 1000, Map.of());
        recovered.restore(BrokerClusterSnapshot.decode(encoded));
        assertThat(new String(encoded, StandardCharsets.UTF_8))
                .contains("\"recoveryEvidence\"")
                .doesNotContain("\"routeSummary\"");
        assertThat(recovered.findOperation(new ClusterOperationId("drain-1")).orElseThrow()
                .drainProgress().recoveryEvidence()).isEqualTo(summary(3L, 2L, 0L, true));
        BrokerClusterConsensusResult completed = recovered.apply(
                BrokerClusterCommandFixture.completeDrain(
                        "drain-1", "digest-1", recovered.committedState().revision(), 2_000L));

        assertThat(completed.status()).isEqualTo(Status.APPLIED);
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
                new RaftBrokerClusterStateMachine("cluster-a", initialState, 1, Map.of());
        applyTopologyChange(stateMachine, BrokerClusterCommandFixture.topology(
                "operation-1", "digest-1", 0L, "ADD_BROKER", "broker-b",
                List.of(active("broker-a"), discovered("broker-b")), 1_000L));

        BrokerClusterConsensusResult full = applyTopologyChange(stateMachine,
                BrokerClusterCommandFixture.topology(
                "operation-2", "digest-2", 2L, "ADD_BROKER", "broker-c",
                List.of(active("broker-a"), discovered("broker-b"), discovered("broker-c")),
                2_000L));
        assertThat(full.status()).isEqualTo(Status.UNAVAILABLE);

        stateMachine.apply(BrokerClusterCommandFixture.advanceOperation(
                "operation-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.FAILED, "test failure", 3_000L));
        BrokerClusterConsensusResult applied = applyTopologyChange(stateMachine,
                BrokerClusterCommandFixture.topology(
                "operation-2", "digest-2", 2L, "ADD_BROKER", "broker-c",
                List.of(active("broker-a"), discovered("broker-b"), discovered("broker-c")),
                4_000L));

        assertThat(applied.status()).isEqualTo(Status.APPLIED);
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
            BrokerRaftAdapter repository,
            BrokerClusterOperationProposal command) {
        BrokerClusterConsensusResult begun = BrokerRaftTestBindings.submit(repository,
                BrokerClusterCommandFixture.beginOperation(command)).toCompletableFuture().join();
        if (!begun.accepted()) {
            return begun;
        }
        if (begun.operation().phase() == BrokerClusterOperationPhase.FENCING_OLD_EPOCH) {
            BrokerRaftTestBindings.submit(repository, BrokerClusterCommandFixture.fenceEpoch(fenceFor(
                    repository.getCommittedState(), command.operationId().value()))).toCompletableFuture().join();
        }
        return BrokerRaftTestBindings.submit(repository, BrokerClusterCommandFixture.commitOperation(command))
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

    private BrokerRaftAdapter createAdapter(BrokerRaftProperties properties, BrokerClusterState state)
            throws IOException {
        return new BrokerRaftAdapter(properties, state,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)));
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
        properties.setElectionTimeoutMs(300);
        properties.setSnapshotInterval(Duration.ZERO);
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

    private BrokerRaftAdapter awaitLeader(
            List<BrokerRaftAdapter> repositories) {
        java.util.concurrent.atomic.AtomicReference<BrokerRaftAdapter> leader =
                new java.util.concurrent.atomic.AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            List<BrokerRaftAdapter> leaders = repositories.stream()
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
