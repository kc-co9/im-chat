package com.co.kc.imchat.broker.adapter.cluster;

import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;

import com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand;
import java.util.OptionalLong;
import java.util.Map;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer;
import com.co.kc.imchat.broker.model.cqrs.command.RemoveBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftServiceFactory;
import com.alipay.sofa.jraft.StateMachine;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain;
import org.mockito.MockedStatic;
import org.mockito.AdditionalAnswers;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    void membershipConfigurationEntersRuntimeSynchronouslyOnCallingThread() throws Exception {
        Thread caller = Thread.currentThread();
        CompletableFuture<Thread> entered = new CompletableFuture<>();
        CompletableFuture<com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult> nativeCompletion =
                new CompletableFuture<>();
        try (MockedConstruction<RaftRuntime> runtimes = Mockito.mockConstruction(RaftRuntime.class,
                (runtime, context) -> when(runtime.changeConfiguration(any())).thenAnswer(invocation -> {
                    entered.complete(Thread.currentThread());
                    return nativeCompletion.minimalCompletionStage();
                }));
             BrokerRaftAdapter adapter = createAdapter(properties("broker-a", 18021,
                     temporaryDirectory.resolve("synchronous-entry"), List.of(endpoint(18021))),
                     state(0L, 0L, active("broker-a")))) {
            CompletionStage<?> result = adapter.changeMembershipConfiguration(
                    new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                            Set.of(new MembershipEndpoint(endpoint(18021))), Set.of()));

            assertThat(entered.get(5, TimeUnit.SECONDS)).isSameAs(caller);
            assertThat(result.toCompletableFuture()).isNotDone();
            nativeCompletion.complete(new com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult(
                    com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult.Status.APPLIED, 0, null, null));
            assertThat(result.toCompletableFuture().get(5, TimeUnit.SECONDS)).isNotNull();
        }
    }

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
                if (index == 11) {
                    assertThat(((com.co.kc.imchat.broker.model.cqrs.command.CompleteRouteRecoveryCommand)
                            submitted.get(index)).changedAt().toEpochMilli()).isBetween(before, after);
                    expectedPayload.set("changedAt", actualPayload.get("changedAt"));
                } else if (List.of(1, 2, 3, 5, 6, 7, 8).contains(index)) {
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
    void nodeLeadershipBeforeRolePublicationRejectsDrainConfirmation() throws Exception {
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        AtomicBoolean armed = new AtomicBoolean(true);
        AtomicBoolean gateExpired = new AtomicBoolean();
        AtomicLong callbackTerm = new AtomicLong();
        BrokerRaftAdapter adapter = null;
        try {
            try (MockedStatic<RaftServiceFactory> factory = Mockito.mockStatic(
                    RaftServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
                factory.when(() -> RaftServiceFactory.createRaftNode(Mockito.anyString(), Mockito.any()))
                        .thenAnswer(creation -> {
                            Node realNode = (Node) creation.callRealMethod();
                            Node node = Mockito.mock(Node.class, AdditionalAnswers.delegatesTo(realNode));
                            Mockito.doAnswer(initialization -> {
                                NodeOptions options = initialization.getArgument(0);
                                StateMachine machine = Mockito.spy(options.getFsm());
                                Mockito.doAnswer(callback -> {
                                    if (armed.compareAndSet(true, false)) {
                                        callbackTerm.set(callback.getArgument(0));
                                        callbackEntered.countDown();
                                        try {
                                            if (!releaseCallback.await(10, TimeUnit.SECONDS)) {
                                                gateExpired.set(true);
                                            }
                                        } catch (InterruptedException interrupted) {
                                            gateExpired.set(true);
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    return callback.callRealMethod();
                                }).when(machine).onLeaderStart(Mockito.anyLong());
                                options.setFsm(machine);
                                return realNode.init(options);
                            }).when(node).init(Mockito.any(NodeOptions.class));
                            return node;
                        });
                int port = availablePort();
                adapter = createAdapter(properties("broker-a", port,
                        temporaryDirectory.resolve("role-window"), List.of(endpoint(port))),
                        state(0L, 0L, active("broker-a")));
            }
            assertThat(callbackEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(gateExpired.get()).isFalse();
            assertThat(adapter.getLocalBroker().isLeader()).isTrue();
            assertThat(adapter.getLocalBroker().getLeaderTerm()).isEmpty();
            CompletionStage<BrokerMembershipPermitDrain> rejected = adapter.confirmMembershipPermitDrain(
                    new QuorumReadNonce("role-window-before-publication"));
            assertThatThrownBy(() -> rejected.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("drain confirmation has no current role");
            assertThat(gateExpired.get()).isFalse();
            releaseCallback.countDown();
            BrokerRaftAdapter running = adapter;
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                    running.getLocalBroker().getLeaderTerm()).contains(new LeaderTerm(callbackTerm.get())));
            BrokerMembershipPermitDrain proof = adapter.confirmMembershipPermitDrain(
                    new QuorumReadNonce("role-window-after-publication"))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(proof.leaderTerm()).isEqualTo(new LeaderTerm(callbackTerm.get()));
            assertThat(adapter.isMembershipPermitDrainCurrent(proof)).isTrue();
            assertThat(gateExpired.get()).isFalse();
        } finally {
            releaseCallback.countDown();
            if (adapter != null) {
                adapter.close();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rolePublicationWindowMakesDrainedRetransmissionUnknown(boolean remove) throws Exception {
        List<Integer> ports = List.of(availablePort(), availablePort(), availablePort());
        List<String> voters = ports.stream().map(this::endpoint).toList();
        List<BrokerRaftAdapter> adapters = new ArrayList<>();
        List<BrokerMembershipExecutor> executors = new ArrayList<>();
        List<BrokerMembershipAppService> services = new ArrayList<>();
        List<BrokerPeerAdapter> peers = new ArrayList<>();
        Map<String, AtomicInteger> nativeCalls = new ConcurrentHashMap<>();
        List<ConfirmMembershipPermitDrainedParams> forwarded =
                new CopyOnWriteArrayList<>();
        Map<String, String> drainDiagnostics = new ConcurrentHashMap<>();
        BiConsumer<String, CompletionStage<?>> observeDrain = (stage, result) -> {
            drainDiagnostics.put(stage, "started");
            result.whenComplete((value, failure) -> drainDiagnostics.put(stage,
                    "result=" + value + ", failure=" + failure
                            + ", cause=" + (failure == null ? null : failure.getCause())));
        };
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        AtomicBoolean armed = new AtomicBoolean();
        AtomicBoolean gateExpired = new AtomicBoolean();
        AtomicLong callbackTerm = new AtomicLong();
        AtomicReference<String> oldHolder = new AtomicReference<>();
        AtomicReference<String> gatedNode = new AtomicReference<>();
        AtomicReference<BrokerMembershipPermit> exactPermit = new AtomicReference<>();
        AtomicReference<Throwable> proofFailure = new AtomicReference<>();
        AtomicInteger explicitAttempt = new AtomicInteger();
        AtomicInteger proofCalls = new AtomicInteger();
        AtomicInteger readCalls = new AtomicInteger();
        AtomicInteger submitCalls = new AtomicInteger();
        try {
            BrokerClusterState initial = state(0L, 0L, active("broker-a"), active("broker-b"), active("broker-c"));
            for (int i = 0; i < ports.size(); i++) {
                String id = "broker-" + (char) ('a' + i);
                ThreadPoolExecutor worker = new ThreadPoolExecutor(
                        1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
                        Thread.ofPlatform().daemon().name("t5-real-" + id).factory(),
                        new ThreadPoolExecutor.AbortPolicy());
                BrokerMembershipExecutor executor =
                        new BrokerMembershipExecutor(new BrokerId(id), worker);
                executors.add(executor);
                BrokerRaftProperties settings = properties(id, ports.get(i),
                        temporaryDirectory.resolve("self-" + remove + "-" + i), voters);
                BrokerRaftAdapter adapter;
                try (MockedStatic<RaftServiceFactory> factory = Mockito.mockStatic(
                        RaftServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
                    factory.when(() -> RaftServiceFactory.createRaftNode(Mockito.anyString(), Mockito.any()))
                            .thenAnswer(creation -> {
                                Node realNode = (Node) creation.callRealMethod();
                                Node node = Mockito.mock(Node.class, AdditionalAnswers.delegatesTo(realNode));
                                Mockito.doAnswer(initialization -> {
                                    NodeOptions options = initialization.getArgument(0);
                                    StateMachine machine = Mockito.spy(options.getFsm());
                                    Mockito.doAnswer(callback -> {
                                        if (!id.equals(oldHolder.get()) && armed.compareAndSet(true, false)) {
                                            gatedNode.set(id);
                                            callbackTerm.set(callback.getArgument(0));
                                            callbackEntered.countDown();
                                            try {
                                                if (!releaseCallback.await(10, TimeUnit.SECONDS)) {
                                                    gateExpired.set(true);
                                                }
                                            } catch (InterruptedException interrupted) {
                                                gateExpired.set(true);
                                                Thread.currentThread().interrupt();
                                            }
                                        }
                                        return callback.callRealMethod();
                                    }).when(machine).onLeaderStart(Mockito.anyLong());
                                    options.setFsm(machine);
                                    return realNode.init(options);
                                }).when(node).init(Mockito.any(NodeOptions.class));
                                return node;
                            });
                    adapter = Mockito.spy(new BrokerRaftAdapter(settings, initial,
                            new RoutingLeaseGrantService(Duration.ofSeconds(1)), executor));
                }
                adapters.add(adapter);
                Mockito.doAnswer(call -> {
                    drainDiagnostics.put(id + ":confirm-role", "nodeLeader=" + adapter.getLocalBroker().isLeader()
                            + ", callbackLeaderTerm=" + adapter.getLocalBroker().getLeaderTerm()
                            + ", revision=" + adapter.getCommittedState().revision());
                    CompletionStage<?> result = (CompletionStage<?>) call.callRealMethod();
                    if (explicitAttempt.get() == 1 && id.equals(gatedNode.get())) {
                        proofCalls.incrementAndGet();
                        result.whenComplete((value, failure) -> proofFailure.set(failure));
                    }
                    observeDrain.accept(id + ":confirm-drain", result);
                    return result;
                }).when(adapter).confirmMembershipPermitDrain(Mockito.any());
                Mockito.doAnswer(call -> {
                    CompletionStage<?> result = (CompletionStage<?>) call.callRealMethod();
                    if (explicitAttempt.get() == 1 && id.equals(gatedNode.get())) { readCalls.incrementAndGet(); }
                    observeDrain.accept(id + ":fetch-read-index", result);
                    return result;
                }).when(adapter).fetchQuorumReadIndex();
                Mockito.doAnswer(call -> {
                    CompletionStage<?> result = (CompletionStage<?>) call.callRealMethod();
                    if (explicitAttempt.get() == 1 && id.equals(gatedNode.get())) { submitCalls.incrementAndGet(); }
                    observeDrain.accept(id + ":submit-drained", result);
                    return result;
                }).when(adapter).confirmMembershipPermitDrained(Mockito.any());
                AtomicInteger count = new AtomicInteger();
                nativeCalls.put(id, count);
                Mockito.doAnswer(call -> {
                    assertThat(Thread.currentThread().getName()).isEqualTo("t5-real-" + id);
                    count.incrementAndGet();
                    if (id.equals(oldHolder.get()) && exactPermit.get() == null) {
                        BrokerMembershipPermit permit = adapter.findOperation(new ClusterOperationId("self-change"))
                                .orElseThrow().membershipSteps().getFirst().currentPermit();
                        assertThat(permit).isNotNull();
                        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                            for (BrokerRaftAdapter candidate : adapters) {
                                if (candidate != adapter) {
                                    assertThat(candidate.findOperation(new ClusterOperationId("self-change"))
                                            .orElseThrow().membershipSteps().getFirst().currentPermit()).isEqualTo(permit);
                                }
                            }
                        });
                        exactPermit.set(permit);
                        armed.set(true);
                    }
                    return call.callRealMethod();
                }).when(adapter).changeMembershipConfiguration(Mockito.any());
                BrokerPeerAdapter peer = Mockito.mock(BrokerPeerAdapter.class);
                peers.add(peer);
                services.add(new BrokerMembershipAppService(
                        adapter, peer, new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                        new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository())));
            }
            // 仅替换 peer 传输；实际应用校验、专用 drain ReadIndex、FSM CAS 和 JRaft 网络保持真实。
            for (int i = 0; i < peers.size(); i++) {
                String caller = "broker-" + (char) ('a' + i);
                Mockito.when(peers.get(i).confirmMembershipPermitDrained(Mockito.any(), Mockito.any())).thenAnswer(call -> {
                    ConfirmMembershipPermitDrainedParams request = call.getArgument(1);
                    assertThat(explicitAttempt.get()).as("automatic forwarding would make the experiment ambiguous").isPositive();
                    forwarded.add(request);
                    int receiver = request.receiverBrokerId().charAt(request.receiverBrokerId().length() - 1) - 'a';
                    CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> result = services.get(receiver)
                            .confirmExecution(acceptedFact(request, caller));
                    observeDrain.accept(caller + ":receiver-" + receiver, result);
                    return result.thenApply(com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE::membershipPermitDrainedResponseFrom);
                });
            }
            BrokerRaftAdapter oldLeader = awaitLeader(adapters);
            int oldIndex = adapters.indexOf(oldLeader);
            String oldId = "broker-" + (char) ('a' + oldIndex);
            String oldEndpoint = voters.get(oldIndex);
            oldHolder.set(oldId);
            // 当前Leader的业务节点先显式变为INACTIVE；Raft voter资格仍保留。
            BrokerClusterAppService routing =
                    Mockito.spy(new BrokerClusterAppService(oldLeader, peers.get(oldIndex),
                            new InMemoryGatewayRegistry(),
                            Duration.ofMillis(1), Duration.ofMillis(1),
                            Mockito.mock(ScheduledExecutorService.class), Duration.ofSeconds(10),
                            null, null, Duration.ofSeconds(60)));
            Mockito.doReturn(CompletableFuture.completedFuture(true)).when(routing).activateLease(Mockito.any());
            Mockito.when(peers.get(oldIndex).fence(Mockito.any(), Mockito.any())).thenAnswer(call -> {
                com.co.kc.imchat.broker.domain.cluster.model.BrokerNode broker = call.getArgument(0);
                RoutingEpochFence fence = call.getArgument(1);
                return CompletableFuture.completedFuture(FenceAck.builder()
                        .brokerId(broker.id()).routingEpoch(fence.epoch()).leaderTerm(fence.leaderTerm())
                        .fencedLeaseSequence(leaseSequence(1L))
                        .operationId(fence.operationId()).build());
            });
            routing.forceDeactivate(BrokerManagementHttpTransformer.INSTANCE
                    .forceDeactivateCommand("self-inactive", oldId, 0L, "controlled self maintenance"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(oldLeader.getClusterSnapshot().findNode(new BrokerId(oldId)).orElseThrow().trafficState().name())
                    .isEqualTo("INACTIVE");
            BrokerClusterOperation deactivation = oldLeader.findOperation(new ClusterOperationId("self-inactive")).orElseThrow();
            assertThat(oldLeader.saveRecoveryEvidence(deactivation, summary(0, 0, 0, true), Instant.now())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).accepted()).isTrue();
            assertThat(oldLeader.verifyRouteRecovery(deactivation, Duration.ofSeconds(10),
                    Instant.now()).toCompletableFuture().get(5, TimeUnit.SECONDS).accepted()).isTrue();
            assertThat(oldLeader.completeRouteRecovery(deactivation).toCompletableFuture().get(5, TimeUnit.SECONDS).accepted()).isTrue();
            long epoch = oldLeader.getCommittedState().epoch();
            CompletionStage<?> original = remove
                    ? services.get(oldIndex).removeBroker(new RemoveBrokerCommand(
                            "self-change", oldId, epoch, oldEndpoint))
                    : services.get(oldIndex).demoteVoter(new DemoteVoterCommand(
                            "self-change", oldId, epoch, oldEndpoint));
            List<BrokerRaftAdapter> remaining = adapters.stream().filter(adapter -> adapter != oldLeader).toList();
            BrokerRaftAdapter newLeader = awaitLeader(remaining);
            assertThat(executors.get(oldIndex).undrainedPermitCount()).isZero();
            assertThat(oldLeader.getLeaderEndpoint()).isNull();
            original.toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(callbackEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(gateExpired.get()).isFalse();
            assertThat(newLeader.getLocalBroker().getId().value()).isEqualTo(gatedNode.get());
            assertThat(newLeader.getLocalBroker().isLeader()).isTrue();
            assertThat(newLeader.getLocalBroker().getLeaderTerm()).isEmpty();
            assertThat(newLeader.findOperation(new ClusterOperationId("self-change")).orElseThrow()
                    .membershipSteps().getFirst().currentPermit()).isEqualTo(exactPermit.get());
            assertThat(forwarded).isEmpty();
            Map<String, Integer> nativeBefore = new HashMap<>();
            nativeCalls.forEach((id, count) -> nativeBefore.put(id, count.get()));
            explicitAttempt.set(1);
            MembershipConfirmationResult unknown = services.get(oldIndex)
                    .sendExecutionResult(new ClusterOperationId("self-change"), newLeader.getLocalBroker().getId())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(unknown.status()).withFailMessage("response=%s, stages=%s", unknown, drainDiagnostics)
                    .isEqualTo(MembershipConfirmationResult.Status.UNKNOWN);
            assertThat(unknown.message()).isEqualTo("confirmation outcome is unknown");
            assertThat(forwarded).hasSize(1);
            assertThat(proofCalls.get()).isEqualTo(1);
            assertThat(proofFailure.get()).isInstanceOf(CompletionException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("drain confirmation has no current role");
            assertThat(readCalls.get()).isZero();
            assertThat(submitCalls.get()).isZero();
            assertThat(newLeader.findOperation(new ClusterOperationId("self-change")).orElseThrow()
                    .membershipSteps().getFirst().hasUndrainedPermit()).isTrue();
            assertThat(gateExpired.get()).isFalse();
            releaseCallback.countDown();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                    newLeader.getLocalBroker().getLeaderTerm()).contains(new LeaderTerm(callbackTerm.get())));
            explicitAttempt.set(2);
            MembershipConfirmationResult forwardedResult = services.get(oldIndex)
                    .sendExecutionResult(new ClusterOperationId("self-change"), newLeader.getLocalBroker().getId())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(forwardedResult.status()).withFailMessage("response=%s, stages=%s", forwardedResult, drainDiagnostics)
                    .isIn(MembershipConfirmationResult.Status.COMMITTED, MembershipConfirmationResult.Status.DUPLICATE);
            assertThat(forwarded).hasSize(2);
            assertThat(forwarded.get(1)).isEqualTo(forwarded.getFirst());
            assertThat(forwarded.getFirst().permitId()).isEqualTo(exactPermit.get().permitId());
            nativeCalls.forEach((id, count) -> assertThat(count.get()).isEqualTo(nativeBefore.get(id)));
            assertThat(gateExpired.get()).isFalse();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(forwarded).isNotEmpty();
                BrokerClusterOperation operation = newLeader.findOperation(new ClusterOperationId("self-change")).orElseThrow();
                assertThat(operation.membershipSteps().getFirst().successful()).isTrue();
                assertThat(operation.membershipSteps().getFirst().hasUndrainedPermit()).isFalse();
            });
            ConfirmMembershipPermitDrainedParams drain = forwarded.getFirst();
            BrokerClusterOperation firstDrained = newLeader.findOperation(new ClusterOperationId("self-change")).orElseThrow();
            assertThat(drain.permitId()).isEqualTo(firstDrained.membershipSteps().getFirst().currentPermit().permitId());
            assertThat(drain.executorBrokerId()).isEqualTo(oldId);
            assertThat(drain.receiverBrokerId()).isEqualTo(newLeader.getLocalBroker().getId().value());
            assertThat(newLeader.readLeaderMembershipConfiguration().voters()).doesNotContain(new MembershipEndpoint(oldEndpoint));
            int newIndex = adapters.indexOf(newLeader);
            services.get(newIndex).resume(new ClusterOperationId("self-change")).toCompletableFuture().get(15, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(newLeader
                    .findOperation(new ClusterOperationId("self-change")).orElseThrow().phase())
                    .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED));
            Set<MembershipEndpoint> expectedVoters = voters.stream().filter(value -> !value.equals(oldEndpoint))
                    .map(MembershipEndpoint::new).collect(Collectors.toSet());
            assertThat(newLeader.readLeaderMembershipConfiguration().voters()).isEqualTo(expectedVoters);
            assertThat(newLeader.readLeaderMembershipConfiguration().learners()).isEqualTo(remove ? Set.of()
                    : Set.of(new MembershipEndpoint(oldEndpoint)));
            int beforeLate = nativeCalls.get(oldId).get();
            original.toCompletableFuture().get(10, TimeUnit.SECONDS);
            services.get(oldIndex).resume(new ClusterOperationId("self-change")).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(nativeCalls.get(oldId).get()).isEqualTo(beforeLate);
            assertThat(newLeader.readLeaderMembershipConfiguration().voters()).isEqualTo(expectedVoters);
            if (!remove) {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(oldLeader
                        .findOperation(new ClusterOperationId("self-change")).orElseThrow().phase())
                        .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED));
            }
            BrokerClusterOperationProposal write = BrokerClusterCommandFixture.topology("post-self-write", "write",
                    newLeader.getCommittedState().revision(), "ADD_BROKER", "broker-new",
                    Stream.concat(newLeader.getCommittedState().brokers().stream(),
                            Stream.of(discovered("broker-new"))).toList(), 2_000L);
            assertThat(submitTopologyChange(newLeader, write).accepted()).isTrue();
            if (!remove) {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(oldLeader.getCommittedState().revision())
                        .isEqualTo(newLeader.getCommittedState().revision()));
            }
        } finally {
            releaseCallback.countDown();
            for (BrokerRaftAdapter adapter : adapters) { adapter.close(); }
            executors.forEach(BrokerMembershipExecutor::close);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void selfReductionDrainsExactOldPermitOnNewLeaderWithoutLateConfigurationRollback(boolean remove) throws Exception {
        List<Integer> ports = List.of(availablePort(), availablePort(), availablePort());
        List<String> voters = ports.stream().map(this::endpoint).toList();
        List<BrokerRaftAdapter> adapters = new ArrayList<>();
        List<com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor> executors = new ArrayList<>();
        List<com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService> services = new ArrayList<>();
        List<com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter> peers = new ArrayList<>();
        Map<String, java.util.concurrent.atomic.AtomicInteger> nativeCalls = new java.util.concurrent.ConcurrentHashMap<>();
        List<com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams> forwarded =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        Map<String, String> drainDiagnostics = new ConcurrentHashMap<>();
        BiConsumer<String, CompletionStage<?>> observeDrain = (stage, result) -> {
            drainDiagnostics.put(stage, "started");
            result.whenComplete((value, failure) -> drainDiagnostics.put(stage,
                    "result=" + value + ", failure=" + failure
                            + ", cause=" + (failure == null ? null : failure.getCause())));
        };
        try {
            BrokerClusterState initial = state(0L, 0L, active("broker-a"), active("broker-b"), active("broker-c"));
            for (int i = 0; i < ports.size(); i++) {
                String id = "broker-" + (char) ('a' + i);
                java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                        1, 1, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                        Thread.ofPlatform().daemon().name("t5-real-" + id).factory(),
                        new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
                com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor executor =
                        new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor(new BrokerId(id), worker);
                executors.add(executor);
                BrokerRaftProperties settings = properties(id, ports.get(i),
                        temporaryDirectory.resolve("self-" + remove + "-" + i), voters);
                BrokerRaftAdapter adapter = Mockito.spy(new BrokerRaftAdapter(settings, initial,
                        new RoutingLeaseGrantService(Duration.ofSeconds(1)), executor));
                adapters.add(adapter);
                Mockito.doAnswer(call -> {
                    drainDiagnostics.put(id + ":confirm-role", "nodeLeader=" + adapter.getLocalBroker().isLeader()
                            + ", callbackLeaderTerm=" + adapter.getLocalBroker().getLeaderTerm()
                            + ", revision=" + adapter.getCommittedState().revision());
                    CompletionStage<?> result = (CompletionStage<?>) call.callRealMethod();
                    observeDrain.accept(id + ":confirm-drain", result);
                    return result;
                }).when(adapter).confirmMembershipPermitDrain(Mockito.any());
                Mockito.doAnswer(call -> {
                    CompletionStage<?> result = (CompletionStage<?>) call.callRealMethod();
                    observeDrain.accept(id + ":fetch-read-index", result);
                    return result;
                }).when(adapter).fetchQuorumReadIndex();
                Mockito.doAnswer(call -> {
                    CompletionStage<?> result = (CompletionStage<?>) call.callRealMethod();
                    observeDrain.accept(id + ":submit-drained", result);
                    return result;
                }).when(adapter).confirmMembershipPermitDrained(Mockito.any());
                java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
                nativeCalls.put(id, count);
                Mockito.doAnswer(call -> {
                    assertThat(Thread.currentThread().getName()).isEqualTo("t5-real-" + id);
                    count.incrementAndGet();
                    return call.callRealMethod();
                }).when(adapter).changeMembershipConfiguration(Mockito.any());
                BrokerPeerAdapter peer = Mockito.mock(BrokerPeerAdapter.class);
                peers.add(peer);
                services.add(new com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService(
                        adapter, peer, new com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount(1), executor,
                        new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                        new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository())));
            }
            // 仅替换 peer 传输；实际应用校验、专用 drain ReadIndex、FSM CAS 和 JRaft 网络保持真实。
            for (int i = 0; i < peers.size(); i++) {
                String caller = "broker-" + (char) ('a' + i);
                Mockito.when(peers.get(i).confirmMembershipPermitDrained(Mockito.any(), Mockito.any())).thenAnswer(call -> {
                    com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams request = call.getArgument(1);
                    forwarded.add(request);
                    int receiver = request.receiverBrokerId().charAt(request.receiverBrokerId().length() - 1) - 'a';
                    CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> result = services.get(receiver)
                            .confirmExecution(acceptedFact(request, caller));
                    observeDrain.accept(caller + ":receiver-" + receiver, result);
                    return result.thenApply(com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE::membershipPermitDrainedResponseFrom);
                });
            }
            BrokerRaftAdapter oldLeader = awaitLeader(adapters);
            int oldIndex = adapters.indexOf(oldLeader);
            String oldId = "broker-" + (char) ('a' + oldIndex);
            String oldEndpoint = voters.get(oldIndex);
            // 当前Leader的业务节点先显式变为INACTIVE；Raft voter资格仍保留。
            com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService routing =
                    Mockito.spy(new com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService(oldLeader, peers.get(oldIndex),
                            new com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry(),
                            Duration.ofMillis(1), Duration.ofMillis(1),
                            Mockito.mock(java.util.concurrent.ScheduledExecutorService.class), Duration.ofSeconds(10),
                            null, null, Duration.ofSeconds(60)));
            Mockito.doReturn(CompletableFuture.completedFuture(true)).when(routing).activateLease(Mockito.any());
            Mockito.when(peers.get(oldIndex).fence(Mockito.any(), Mockito.any())).thenAnswer(call -> {
                com.co.kc.imchat.broker.domain.cluster.model.BrokerNode broker = call.getArgument(0);
                RoutingEpochFence fence = call.getArgument(1);
                return CompletableFuture.completedFuture(com.co.kc.imchat.broker.domain.authority.model.FenceAck.builder()
                        .brokerId(broker.id()).routingEpoch(fence.epoch()).leaderTerm(fence.leaderTerm())
                        .fencedLeaseSequence(com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence(1L))
                        .operationId(fence.operationId()).build());
            });
            routing.forceDeactivate(com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer.INSTANCE
                    .forceDeactivateCommand("self-inactive", oldId, 0L, "controlled self maintenance"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(oldLeader.getClusterSnapshot().findNode(new BrokerId(oldId)).orElseThrow().trafficState().name())
                    .isEqualTo("INACTIVE");
            BrokerClusterOperation deactivation = oldLeader.findOperation(new ClusterOperationId("self-inactive")).orElseThrow();
            assertThat(oldLeader.saveRecoveryEvidence(deactivation, summary(0, 0, 0, true), Instant.now())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).accepted()).isTrue();
            assertThat(oldLeader.verifyRouteRecovery(deactivation, Duration.ofSeconds(10),
                    Instant.now()).toCompletableFuture().get(5, TimeUnit.SECONDS).accepted()).isTrue();
            assertThat(oldLeader.completeRouteRecovery(deactivation).toCompletableFuture().get(5, TimeUnit.SECONDS).accepted()).isTrue();
            long epoch = oldLeader.getCommittedState().epoch();
            java.util.concurrent.CompletionStage<?> original = remove
                    ? services.get(oldIndex).removeBroker(new com.co.kc.imchat.broker.model.cqrs.command.RemoveBrokerCommand(
                            "self-change", oldId, epoch, oldEndpoint))
                    : services.get(oldIndex).demoteVoter(new com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand(
                            "self-change", oldId, epoch, oldEndpoint));
            List<BrokerRaftAdapter> remaining = adapters.stream().filter(adapter -> adapter != oldLeader).toList();
            BrokerRaftAdapter newLeader = awaitLeader(remaining);
            assertThat(executors.get(oldIndex).undrainedPermitCount()).isZero();
            assertThat(oldLeader.getLeaderEndpoint()).isNull();
            original.toCompletableFuture().get(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(15)).until(() -> newLeader.getLocalBroker().getLeaderTerm().isPresent());
            LeaderTerm publishedTerm = newLeader.getLocalBroker().getLeaderTerm().orElseThrow();
            BrokerMembershipPermitDrain readyProof = newLeader.confirmMembershipPermitDrain(
                    new QuorumReadNonce("self-reduction-retransmit-readiness"))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            long appliedReadIndex = newLeader.fetchQuorumReadIndex().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(appliedReadIndex).isGreaterThanOrEqualTo(readyProof.readIndex().value());
            assertThat(readyProof.leaderTerm()).isEqualTo(publishedTerm);
            assertThat(newLeader.isMembershipPermitDrainCurrent(readyProof)).isTrue();
            assertThat(newLeader.getLocalBroker().getLeaderTerm()).contains(publishedTerm);
            String beforeForward = "nodeLeader=" + newLeader.getLocalBroker().isLeader()
                    + ", callbackLeaderTerm=" + newLeader.getLocalBroker().getLeaderTerm()
                    + ", revision=" + newLeader.getCommittedState().revision();
            com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult forwardedResult = services.get(oldIndex)
                    .sendExecutionResult(new ClusterOperationId("self-change"), newLeader.getLocalBroker().getId())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            String afterForward = "nodeLeader=" + newLeader.getLocalBroker().isLeader()
                    + ", callbackLeaderTerm=" + newLeader.getLocalBroker().getLeaderTerm()
                    + ", revision=" + newLeader.getCommittedState().revision();
            assertThat(forwardedResult.status()).withFailMessage(
                    "remove=%s, oldHolder=%s, newLeader=%s, forwardedResult=%s, before={%s}, after={%s}, stages=%s",
                    remove, oldId, newLeader.getLocalBroker().getId(), forwardedResult,
                    beforeForward, afterForward, drainDiagnostics).isIn(
                    com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED,
                    com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.DUPLICATE);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(forwarded).isNotEmpty();
                BrokerClusterOperation operation = newLeader.findOperation(new ClusterOperationId("self-change")).orElseThrow();
                assertThat(operation.membershipSteps().getFirst().successful()).isTrue();
                assertThat(operation.membershipSteps().getFirst().hasUndrainedPermit()).isFalse();
            });
            com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams drain = forwarded.getFirst();
            BrokerClusterOperation firstDrained = newLeader.findOperation(new ClusterOperationId("self-change")).orElseThrow();
            assertThat(drain.permitId()).isEqualTo(firstDrained.membershipSteps().getFirst().currentPermit().permitId());
            assertThat(drain.executorBrokerId()).isEqualTo(oldId);
            assertThat(drain.receiverBrokerId()).isEqualTo(newLeader.getLocalBroker().getId().value());
            assertThat(newLeader.readLeaderMembershipConfiguration().voters()).doesNotContain(new MembershipEndpoint(oldEndpoint));
            int newIndex = adapters.indexOf(newLeader);
            services.get(newIndex).resume(new ClusterOperationId("self-change")).toCompletableFuture().get(15, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(newLeader
                    .findOperation(new ClusterOperationId("self-change")).orElseThrow().phase())
                    .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED));
            Set<MembershipEndpoint> expectedVoters = voters.stream().filter(value -> !value.equals(oldEndpoint))
                    .map(MembershipEndpoint::new).collect(java.util.stream.Collectors.toSet());
            assertThat(newLeader.readLeaderMembershipConfiguration().voters()).isEqualTo(expectedVoters);
            assertThat(newLeader.readLeaderMembershipConfiguration().learners()).isEqualTo(remove ? Set.of()
                    : Set.of(new MembershipEndpoint(oldEndpoint)));
            int beforeLate = nativeCalls.get(oldId).get();
            original.toCompletableFuture().get(10, TimeUnit.SECONDS);
            services.get(oldIndex).resume(new ClusterOperationId("self-change")).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(nativeCalls.get(oldId).get()).isEqualTo(beforeLate);
            assertThat(newLeader.readLeaderMembershipConfiguration().voters()).isEqualTo(expectedVoters);
            if (!remove) {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(oldLeader
                        .findOperation(new ClusterOperationId("self-change")).orElseThrow().phase())
                        .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED));
            }
            BrokerClusterOperationProposal write = BrokerClusterCommandFixture.topology("post-self-write", "write",
                    newLeader.getCommittedState().revision(), "ADD_BROKER", "broker-new",
                    java.util.stream.Stream.concat(newLeader.getCommittedState().brokers().stream(),
                            java.util.stream.Stream.of(discovered("broker-new"))).toList(), 2_000L);
            assertThat(submitTopologyChange(newLeader, write).accepted()).isTrue();
            if (!remove) {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(oldLeader.getCommittedState().revision())
                        .isEqualTo(newLeader.getCommittedState().revision()));
            }
        } finally {
            for (BrokerRaftAdapter adapter : adapters) { adapter.close(); }
            executors.forEach(com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor::close);
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

    @Test
    void localRaftPeerIdPreservesInitializedNodeIndexAndPriority() throws Exception {
        int port = availablePort();
        String configured = endpoint(port) + ":2:10";
        BrokerRaftProperties settings = properties("broker-a", port,
                temporaryDirectory.resolve("local-peer-id"), List.of(configured));
        settings.setEndpoint(configured);
        try (BrokerRaftAdapter adapter = createAdapter(settings,
                settings.initialClusterState(BrokerSelectionAlgorithm.RENDEZVOUS_HASH))) {
            assertThat(adapter.getLocalRaftPeerId()).isEqualTo(endpoint(port) + ":2");
            assertThat(adapter.getLocalRaftPeerId()).isNotEqualTo(configured);
        }
    }

    private BrokerRaftAdapter createAdapter(BrokerRaftProperties properties, BrokerClusterState state)
            throws IOException {
        return new BrokerRaftAdapter(properties, state,
                new RoutingLeaseGrantService(Duration.ofSeconds(1)), new com.co.kc.imchat.plugin.raft.spi.RaftRoleListener() {
                    private final FaultLatch fault = new FaultLatch();
                    private long generation;
                    @Override
                    public FaultLatch faultLatch() { return fault; }
                    @Override
                    public void onRoleChanged(long term, long nextGeneration) {
                        assertThat(nextGeneration).isGreaterThan(generation);
                        generation = nextGeneration;
                    }
                });
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
    private static AcceptMembershipPermitDrainedCommand acceptedFact(
            com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams request,
            String authenticatedExecutorBrokerId) {
        return new AcceptMembershipPermitDrainedCommand(request.clusterId(), request.callerBrokerId(),
                request.receiverBrokerId(), request.operationId(), request.stepId(), request.permitId(),
                request.executorBrokerId(), request.executorIncarnation(), request.targetDigest(),
                AcceptMembershipPermitDrainedCommand.Result.valueOf(request.result().name()), authenticatedExecutorBrokerId);
    }

}
