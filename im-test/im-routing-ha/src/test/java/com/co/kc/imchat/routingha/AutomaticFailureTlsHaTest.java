package com.co.kc.imchat.routingha;

import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.embedded.CaffeineCacheBuilder;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipStep;
import com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand;
import com.co.kc.imchat.broker.domain.cluster.model.VoterDemotionIntent;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerProbeQuery;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand;
import com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.broker.application.cluster.BrokerQuorumAppService;
import com.co.kc.imchat.broker.application.local.LocalLeaseAppService;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.domain.connection.model.GatewayHost;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPort;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPrincipalId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestInterceptorChain;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerCallerIdentityInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerHandlerInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerPeerAdmissionInterceptor;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.domain.authority.FileFenceWatermarkRepository;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerHealthReportHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.MembershipPermitDrainedHandler;
import com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerProbeHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.QuorumReadIndexHandler;
import com.co.kc.imchat.broker.interfaces.handler.routing.RouteLeaseInstallHandler;
import com.co.kc.imchat.broker.interfaces.handler.routing.RouteLeasePrepareHandler;
import com.co.kc.imchat.broker.interfaces.handler.routing.RoutingFenceHandler;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmGatewayRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthReportDTO;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.common.utils.HashUtils;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.server.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import io.netty.channel.Channel;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.function.BiFunction;
import org.assertj.core.api.ThrowingConsumer;
import java.util.function.Supplier;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.awaitility.Awaitility.await;

/**
 * 隔离运行时证据：真实三节点 JRaft、三个独立 mTLS 身份、生产观察/准入/fence/Lease。
 * Gateway 进程及路由重建被显式 ACK 输入替代；不宣称覆盖容器、WebSocket 或真实 Gateway 重建。
 */
class AutomaticFailureTlsHaTest {
    private static final String CLUSTER = "automatic-failure-ha";
    private static final List<String> IDS = List.of("broker-a", "broker-b", "broker-c");
    private static final Duration BUDGET = Duration.ofSeconds(45);
    // 仅本测试缩短时窗；quarantine 仍大于本测试实际签发的 Lease 时长。
    private static final Duration LEASE = Duration.ofMillis(600);
    private static final Duration QUARANTINE = Duration.ofMillis(900);
    private static final Duration STABLE_WINDOW = Duration.ofSeconds(2);
    private static final Duration FRESHNESS = Duration.ofSeconds(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(2);
    private static final Duration RPC_TIMEOUT = Duration.ofMillis(500);
    @TempDir static Path certificates;
    @TempDir Path directory;
    private static List<Path> stores;

    @BeforeAll
    static void createCertificates() throws Exception {
        stores = RoutingHaCertificates.create(certificates, IDS);
    }

    @Test
    void stoppedFollowerAutomaticallyFencesAndRebuildsWithIndependentTlsReports() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node leader = cluster.verifyHealthyAndLeased();
            Node failed = cluster.nodes.stream().filter(node -> node != leader).findFirst().orElseThrow();
            failed.stop();
            BrokerClusterOperation operation = cluster.awaitAutomaticRemoval(failed);
            cluster.assertIsolationAndLeases(operation, failed);
            cluster.acknowledgeGatewayAndAwaitSuccess(operation);
        }
    }

    @Test
    void followerDemotionCommitsBothNativeStepsAfterPermitConfirmation() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node leader = cluster.verifyHealthyAndLeased();
            Node target = cluster.nodes.stream().filter(node -> node != leader).findFirst().orElseThrow();
            MembershipEndpoint endpoint = new MembershipEndpoint(cluster.voters.get(IDS.indexOf(target.id)));
            assertThat(leader.membership.demoteVoter(new DemoteVoterCommand(
                    "tls-demote-follower", target.id, 0L, endpoint.value()))
                    .toCompletableFuture().get(30, TimeUnit.SECONDS).status())
                    .isEqualTo("APPLIED");
            BrokerClusterOperation operation = leader.raft.findOperation(
                    new ClusterOperationId("tls-demote-follower")).orElseThrow();
            assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
            assertThat(operation.membershipSteps()).hasSize(2).allSatisfy(step -> {
                assertThat(step.successful()).isTrue();
                assertThat(step.hasUndrainedPermit()).isFalse();
            });
            assertThat(leader.raft.readLeaderMembershipConfiguration().voters())
                    .containsExactlyInAnyOrderElementsOf(cluster.voters.stream()
                            .map(MembershipEndpoint::new).filter(value -> !value.equals(endpoint)).toList());
            assertThat(leader.raft.readLeaderMembershipConfiguration().learners()).containsExactly(endpoint);
            await().atMost(BUDGET).untilAsserted(() -> cluster.nodes.forEach(node ->
                    assertThat(node.raft.getClusterSnapshot().findNode(new BrokerId(target.id))
                            .orElseThrow().membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER)));
        }
    }

    @Test
    void demotedLeaderConfirmsItsDrainedPermitToReplacementOverTlsBeforeExplicitResume() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node previous = cluster.verifyHealthyAndLeased();
            ClusterOperationId operationId = new ClusterOperationId("tls-demote-leader");
            MembershipEndpoint endpoint = new MembershipEndpoint(cluster.voters.get(IDS.indexOf(previous.id)));
            previous.membership.demoteVoter(new DemoteVoterCommand(
                    operationId.value(), previous.id, 0L, endpoint.value()))
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);
            Node replacement = cluster.leader();
            assertThat(replacement).isNotSameAs(previous);
            await().atMost(BUDGET).untilAsserted(() -> {
                BrokerClusterOperation pending = replacement.raft.findOperation(operationId).orElseThrow();
                assertThat(pending.phase()).isEqualTo(BrokerClusterOperationPhase.PENDING);
                assertThat(pending.membershipSteps().getFirst().currentPermit()).isNotNull();
            });
            com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult confirmation = previous.membership.sendExecutionResult(
                    operationId, new BrokerId(replacement.id)).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(confirmation.status()).isIn(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED,
                    com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.DUPLICATE);
            assertThat(replacement.permitConfirmationCallers).contains(RoutingHaCertificates.PREFIX + previous.id);
            replacement.membership.resume(operationId).toCompletableFuture().get(30, TimeUnit.SECONDS);
            BrokerClusterOperation completed = replacement.raft.findOperation(operationId).orElseThrow();
            assertThat(completed.phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
            assertThat(completed.membershipSteps()).hasSize(2).allSatisfy(step -> {
                assertThat(step.successful()).isTrue();
                assertThat(step.hasUndrainedPermit()).isFalse();
            });
            assertThat(replacement.raft.readLeaderMembershipConfiguration().voters())
                    .containsExactlyInAnyOrderElementsOf(cluster.voters.stream()
                            .map(MembershipEndpoint::new).filter(value -> !value.equals(endpoint)).toList());
            assertThat(replacement.raft.readLeaderMembershipConfiguration().learners()).containsExactly(endpoint);
        }
    }

    @Test
    void committedPendingMembershipBlocksRealObservationUntilExplicitWorkflowCompletion() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node leader = cluster.verifyHealthyAndLeased();
            Node target = cluster.nodes.stream().filter(node -> node != leader).findFirst().orElseThrow();
            DemoteVoterCommand command = new DemoteVoterCommand("pending-observation-gate", target.id, 0L,
                    cluster.voters.get(IDS.indexOf(target.id)));
            BrokerClusterOperation requested = leader.raft.getClusterSnapshot().demoteVoter(VoterDemotionIntent.builder()
                    .operationId(new ClusterOperationId(command.operationId())).commandDigest(command.digest())
                    .expectedRevision(leader.raft.getClusterSnapshot().revision()).brokerId(new BrokerId(target.id))
                    .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.now()).build()).operation();
            assertThat(leader.raft.beginOperation(requested).toCompletableFuture()
                    .get(10, TimeUnit.SECONDS).accepted()).isTrue();
            await().atMost(BUDGET).untilAsserted(() -> cluster.nodes.forEach(node -> {
                BrokerClusterOperation pending = node.raft.findOperation(requested.id()).orElseThrow();
                assertThat(pending.phase()).isEqualTo(BrokerClusterOperationPhase.PENDING);
                assertThat(node.membership.membershipMaintenanceBlocked()).isEqualTo(1L);
                assertThat(node.raft.getClusterSnapshot().findObservers()).isEmpty();
                BrokerClusterState state = node.raft.getCommittedState();
                String caller = IDS.stream().filter(id -> !id.equals(node.id)).findFirst().orElseThrow();
                assertThatThrownBy(() -> node.app.probeBroker(new BrokerProbeQuery(CLUSTER, caller, node.id,
                        state.revision(), state.epoch(), "blocked-pending-membership")))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("observation caller is not a current voter");
            }));
            // 实际观察轮不能登记自动操作；随后恢复同一已提交维护，证明门禁不是永久关闭。
            leader.observe().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(leader.raft.getClusterSnapshot().operations()).extracting(BrokerClusterOperation::id)
                    .containsExactly(requested.id());
            leader.membership.resume(requested.id()).toCompletableFuture().get(30, TimeUnit.SECONDS);
            await().atMost(BUDGET).untilAsserted(() -> cluster.nodes.forEach(node -> {
                assertThat(node.raft.findOperation(requested.id()).orElseThrow().phase())
                        .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
                assertThat(node.membership.membershipMaintenanceBlocked()).isZero();
                assertThat(node.raft.getClusterSnapshot().findObservers()).containsExactlyInAnyOrderElementsOf(
                        cluster.nodes.stream().filter(value -> value != target).map(value -> new BrokerId(value.id)).toList());
            }));
        }
    }

    @Test
    void syncingLearnerAloneBlocksObservationUntilExplicitReadIndexConfirmation() throws Exception {
        try (Cluster cluster = new Cluster(directory, true)) {
            Node leader = cluster.leader();
            String learner = IDS.getLast();
            await().atMost(BUDGET).untilAsserted(() -> cluster.nodes.forEach(node -> {
                assertThat(node.raft.getClusterSnapshot().operations()).isEmpty();
                assertThat(node.raft.getClusterSnapshot().findNode(new BrokerId(learner)).orElseThrow()
                        .membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
                assertThat(node.membership.membershipMaintenanceBlocked()).isEqualTo(1L);
                assertThat(node.raft.getClusterSnapshot().findObservers()).isEmpty();
                BrokerClusterState state = node.raft.getCommittedState();
                String caller = IDS.stream().filter(id -> !id.equals(node.id)).findFirst().orElseThrow();
                assertThatThrownBy(() -> node.app.probeBroker(new BrokerProbeQuery(CLUSTER, caller, node.id,
                        state.revision(), state.epoch(), "blocked-syncing-only")))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("observation caller is not a current voter");
            }));
            leader.observe().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(leader.raft.getClusterSnapshot().operations()).isEmpty();
            assertThat(leader.membership.confirmLearner(new ConfirmLearnerCommand("explicit-sync-complete",
                    learner, 0L, cluster.voters.getLast())).toCompletableFuture().get(30, TimeUnit.SECONDS).status()
                    .name()).isEqualTo("APPLIED");
            await().atMost(BUDGET).untilAsserted(() -> cluster.nodes.forEach(node -> {
                assertThat(node.raft.getClusterSnapshot().findNode(new BrokerId(learner)).orElseThrow()
                        .membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER);
                assertThat(node.membership.membershipMaintenanceBlocked()).isZero();
                assertThat(node.raft.getClusterSnapshot().findObservers()).containsExactlyInAnyOrder(
                        new BrokerId(IDS.getFirst()), new BrokerId(IDS.get(1)));
                assertThat(node.raft.findOperation(new ClusterOperationId("explicit-sync-complete"))
                        .orElseThrow().phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
            }));
            Node receiver = cluster.nodes.stream().filter(node -> node != leader).findFirst().orElseThrow();
            BrokerClusterState state = receiver.raft.getCommittedState();
            assertThat(receiver.app.probeBroker(new BrokerProbeQuery(CLUSTER, leader.id, receiver.id,
                    state.revision(), state.epoch(), "sync-completed"))).isNotNull();
        }
    }

    @Test
    void unknownCommittedPermitBlocksReplacementAndRestartedExecutorObservation() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node previous = cluster.verifyHealthyAndLeased();
            Node target = cluster.nodes.stream().filter(node -> node != previous).findFirst().orElseThrow();
            DemoteVoterCommand command = new DemoteVoterCommand("unknown-permit-restart", target.id, 0L,
                    cluster.voters.get(IDS.indexOf(target.id)));
            BrokerClusterOperation requested = previous.raft.getClusterSnapshot().demoteVoter(VoterDemotionIntent.builder()
                    .operationId(new ClusterOperationId(command.operationId())).commandDigest(command.digest())
                    .expectedRevision(previous.raft.getClusterSnapshot().revision()).brokerId(new BrokerId(target.id))
                    .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.now()).build()).operation();
            assertThat(previous.raft.beginOperation(requested).toCompletableFuture()
                    .get(10, TimeUnit.SECONDS).accepted()).isTrue();
            BrokerMembershipStep first = requested.currentMembershipStep();
            previous.membershipExecutor.reserve(requested.id(), first.stepId(), "persisted-unknown", first.target());
            assertThat(previous.raft.registerMembershipPermit(new RegisterMembershipPermitCommand(requested.id(),
                    first.stepId(), "persisted-unknown", new BrokerId(previous.id), previous.membershipExecutor.incarnation(),
                    previous.raft.getClusterSnapshot().revision())).toCompletableFuture()
                    .get(10, TimeUnit.SECONDS).accepted()).isTrue();
            BrokerMembershipPermit original = previous.raft.findOperation(requested.id()).orElseThrow()
                    .currentMembershipStep().currentPermit();
            assertThat(original.result()).isEqualTo(BrokerMembershipPermit.Result.UNKNOWN);
            // 许可已共识登记但尚未进入 native；停止并重启不能把未知事实推断成已排空。
            await().atMost(BUDGET).untilAsserted(() -> cluster.nodes.forEach(node ->
                    assertThat(node.raft.findOperation(requested.id()).orElseThrow()
                            .currentMembershipStep().currentPermit()).isEqualTo(original)));
            Node receiver = target;
            BrokerClusterOperation beforeConfirmation = receiver.raft.findOperation(requested.id()).orElseThrow();
            long revisionBeforeConfirmation = receiver.raft.getCommittedState().revision();
            com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership configurationBeforeConfirmation =
                    previous.raft.readLeaderMembershipConfiguration();
            ConfirmMembershipPermitDrainedParams exact = new ConfirmMembershipPermitDrainedParams(CLUSTER,
                    previous.id, receiver.id, requested.id().value(), first.stepId(), original.permitId(),
                    previous.id, original.executorIncarnation(), first.targetDigest(),
                    ConfirmMembershipPermitDrainedParams.Result.TARGET_NOT_APPLIED);
            assertThatThrownBy(() -> receiver.sendPermitConfirmation(receiver, exact))
                    .isInstanceOf(BoltInvocationException.class);
            assertThat(receiver.permitRejections).containsExactly(
                    "membership permit confirmation caller or receiver mismatch");
            for (String invalid : List.of("incarnation", "permit", "target")) {
                ConfirmMembershipPermitDrainedParams changed = new ConfirmMembershipPermitDrainedParams(CLUSTER,
                        previous.id, receiver.id, requested.id().value(), first.stepId(),
                        "permit".equals(invalid) ? "obsolete-permit" : original.permitId(), previous.id,
                        "incarnation".equals(invalid) ? "obsolete-incarnation" : original.executorIncarnation(),
                        "target".equals(invalid) ? "wrong-target" : first.targetDigest(), exact.result());
                assertThatThrownBy(() -> previous.sendPermitConfirmation(receiver, changed))
                        .isInstanceOf(BoltInvocationException.class);
                assertThat(receiver.permitRejections.getLast())
                        .isEqualTo("membership permit confirmation does not match stored executor and target");
            }
            assertThat(receiver.permitRejections).hasSize(4);
            assertThat(receiver.permitConfirmationCallers).isEmpty();
            assertThat(receiver.raft.findOperation(requested.id()).orElseThrow()
                    .currentMembershipStep().currentPermit()).isEqualTo(original);
            assertThat(receiver.raft.findOperation(requested.id()).orElseThrow()).isEqualTo(beforeConfirmation);
            assertThat(receiver.raft.getCommittedState().revision()).isEqualTo(revisionBeforeConfirmation);
            assertThat(previous.raft.readLeaderMembershipConfiguration()).isEqualTo(configurationBeforeConfirmation);
            assertThat(previous.raft.readLeaderMembershipConfiguration().voters())
                    .containsExactlyInAnyOrderElementsOf(cluster.voters.stream().map(MembershipEndpoint::new).toList());
            previous.stop();
            Node replacement = cluster.leader();
            assertThat(replacement).isNotSameAs(previous);
            replacement.membership.resume(requested.id()).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(replacement.raft.registerMembershipPermit(new RegisterMembershipPermitCommand(requested.id(),
                    first.stepId(), "replacement-attempt", new BrokerId(replacement.id),
                    replacement.membershipExecutor.incarnation(), replacement.raft.getClusterSnapshot().revision()))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).accepted()).isFalse();
            cluster.restart(previous);
            Node restarted = cluster.nodes.get(IDS.indexOf(previous.id));
            assertThat(restarted.membershipExecutor.incarnation()).isNotEqualTo(original.executorIncarnation());
            await().atMost(BUDGET).untilAsserted(() -> cluster.nodes.forEach(node -> {
                assertThat(node.raft.findOperation(requested.id())).isPresent();
                BrokerClusterOperation pending = node.raft.findOperation(requested.id()).orElseThrow();
                assertThat(pending.phase()).isEqualTo(BrokerClusterOperationPhase.PENDING);
                assertThat(pending.currentMembershipStep().currentPermit()).isEqualTo(original);
                assertThat(node.membership.membershipMaintenanceBlocked()).isEqualTo(1L);
                assertThat(node.raft.getClusterSnapshot().findObservers()).isEmpty();
            }));
            assertThatThrownBy(() -> restarted.membershipExecutor.execute(original,
                    () -> { throw new AssertionError("restarted process must not invoke old native permit"); })
                    .toCompletableFuture().join()).hasCauseInstanceOf(SecurityException.class);
            replacement.observe().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(replacement.raft.getClusterSnapshot().operations()).extracting(BrokerClusterOperation::id)
                    .containsExactly(requested.id());
            assertThat(replacement.raft.readLeaderMembershipConfiguration().voters())
                    .containsExactlyInAnyOrderElementsOf(cluster.voters.stream().map(MembershipEndpoint::new).toList());
            assertThat(replacement.raft.readLeaderMembershipConfiguration().learners()).isEmpty();
        }
    }

    @Test
    void stoppedLeaderRequiresFreshReportsAndReplacementCompletesDegradedWithoutGatewayAck() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node failed = cluster.verifyHealthyAndLeased();
            failed.stop();
            Node replacement = cluster.leader();
            assertThat(replacement).isNotSameAs(failed);
            BrokerClusterOperation operation = cluster.awaitAutomaticRemoval(failed);
            cluster.assertIsolationAndLeases(operation, failed);
            await().atMost(BUDGET).untilAsserted(() -> {
                BrokerClusterOperation completed = replacement.raft.findOperation(operation.id()).orElseThrow();
                assertThat(completed.phase()).isEqualTo(BrokerClusterOperationPhase.DEGRADED);
                assertThat(completed.error().value()).contains("gateway-fixture");
            });
            assertThat(replacement.raft.getClusterSnapshot().operations()).hasSize(1);
        }
    }

    @Test
    void replacementLeaderResumesCommittedFencingWithActualMembershipAndTlsPeers() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node previous = cluster.verifyHealthyAndLeased();
            Node target = cluster.nodes.stream().filter(node -> node != previous).findFirst().orElseThrow();
            // 仅隔离目标 RPC，保留真实 Raft voter，使后续 Leader 故障仍有两个 voter 可选举。
            target.stopRpcAndAssertClosed();
            AtomicReference<BrokerClusterOperation> begun = new AtomicReference<>();
            await().pollInterval(Duration.ofMillis(10)).atMost(BUDGET).untilAsserted(() -> {
                List<BrokerClusterOperation> operations = previous.raft.getClusterSnapshot().operations();
                assertThat(operations).hasSize(1);
                BrokerClusterOperation operation = operations.getFirst();
                assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.FENCING_OLD_EPOCH);
                assertThat(previous.raft.getCommittedState().epoch()).isZero();
                begun.set(operation);
            });
            previous.stop();
            Node replacement = cluster.leader();
            assertThat(replacement).isNotSameAs(previous);
            await().atMost(BUDGET).untilAsserted(() ->
                    assertThat(replacement.raft.findOperation(begun.get().id())).isPresent());
            // 旧 Leader 仍是 ACTIVE holder；恢复其持久身份后新 Leader 才能完成所有存活 holder 的 Lease 安装。
            cluster.restart(previous);
            await().atMost(BUDGET).untilAsserted(() -> {
                Node current = cluster.live().stream().filter(node -> node.raft.getLocalBroker().isLeader())
                        .findFirst().orElseThrow();
                assertThat(current.raft.getClusterSnapshot().findObservers())
                        .containsExactlyInAnyOrderElementsOf(IDS.stream().map(BrokerId::new).toList());
                assertThat(current.raft.findOperation(begun.get().id()).orElseThrow().phase())
                        .isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
                assertThat(current.raft.getCommittedState().epoch()).isEqualTo(1L);
                assertThat(current.raft.getClusterSnapshot().operations()).hasSize(1);
                assertThat(current.raft.getCommittedState().brokers().stream()
                        .filter(broker -> broker.brokerId().equals(target.id)).findFirst().orElseThrow().routingState())
                        .isEqualTo(BrokerRoutingState.INACTIVE);
            });
            cluster.acknowledgeGatewayAndAwaitSuccess(begun.get());
        }
    }

    static void awaitInitialMembership(Duration budget, Runnable readiness) {
        await().atMost(budget).untilAsserted(readiness::run);
    }

    static <L, T> L awaitInitialLeases(Duration budget, ThrowingRunnable healthy, Supplier<L> currentLeader,
            List<T> targets, BiFunction<L, T, CompletionStage<Boolean>> activate, ThrowingConsumer<T> assertLocalLease) {
        class Recovery {
            private int next;
            private L leader;
            private CompletableFuture<Boolean> pending;
        }
        class LeaseProofFailure extends RuntimeException {
            private LeaseProofFailure(AssertionError cause) {
                super(cause);
            }
        }
        Recovery recovery = new Recovery();
        // 只观察实际完成；预算耗尽不取消底层调用，也不为未完成调用开启下一轮。
        try {
            await().pollInSameThread().pollInterval(Duration.ofMillis(10)).atMost(budget).untilAsserted(() -> {
                if (recovery.pending != null) {
                    assertThat(recovery.pending.isDone())
                            .as("initial Lease pending: target=%s, stage=%s", targets.get(recovery.next), recovery.pending)
                            .isTrue();
                    boolean installed = recovery.pending.join();
                    recovery.pending = null;
                    if (installed) {
                        try {
                            assertLocalLease.accept(targets.get(recovery.next));
                        } catch (AssertionError failure) {
                            // 本机执行证明失败不是待就绪状态；禁止 Awaitility 吞掉后重放安装。
                            throw new LeaseProofFailure(failure);
                        }
                        recovery.next++;
                    }
                }
                healthy.run();
                L current = currentLeader.get();
                assertThat(current).as("unique live Leader during initial Lease recovery").isNotNull();
                if (recovery.leader != null && !recovery.leader.equals(current)) {
                    recovery.next = 0;
                }
                recovery.leader = current;
                if (recovery.next == targets.size()) {
                    return;
                }
                recovery.pending = activate.apply(current, targets.get(recovery.next)).toCompletableFuture();
                assertThat(recovery.pending).as("initial Lease awaiting completion: target=%s",
                        targets.get(recovery.next)).isNull();
            });
        } catch (LeaseProofFailure failure) {
            throw (AssertionError) failure.getCause();
        }
        return recovery.leader;
    }

    private static final class Cluster implements AutoCloseable {
        private final List<Node> nodes = new CopyOnWriteArrayList<>();
        private final Path directory;
        private final List<String> voters;
        private final List<String> routes;
        private final ScheduledExecutorService ticks = Executors.newSingleThreadScheduledExecutor();
        private final List<Throwable> tickFailures = new CopyOnWriteArrayList<>();

        private Cluster(Path directory) throws Exception {
            this(directory, false);
        }

        private Cluster(Path directory, boolean syncingLearner) throws Exception {
            this.directory = directory;
            try (EndpointReservations reservations = new EndpointReservations()) {
                List<String> endpoints = reservations.endpoints();
                voters = endpoints.subList(0, 3);
                routes = endpoints.subList(3, 6);
                for (int index = 0; index < 3; index++) {
                    nodes.add(new Node(index, directory, voters, routes, reservations, syncingLearner));
                }
                leader();
                awaitInitialMembership(BUDGET, () -> nodes.forEach(node -> {
                    assertThat(node.raft.getClusterSnapshot().findObservers())
                            .containsExactlyInAnyOrderElementsOf(syncingLearner ? List.of()
                                    : IDS.stream().map(BrokerId::new).toList());
                }));
                ticks.scheduleWithFixedDelay(this::tick, 0, 100, TimeUnit.MILLISECONDS);
            } catch (Exception | AssertionError failure) {
                try {
                    close();
                } catch (Exception | AssertionError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        private void restart(Node previous) throws Exception {
            int index = IDS.indexOf(previous.id);
            previous.close();
            // 重启保留固定身份/端点；初次构造的 reservation 已移交，不再次释放。
            nodes.set(index, new Node(index, directory, voters, routes, null));
        }

        private void tick() {
            for (Node node : live()) {
                try {
                    node.observe().whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            tickFailures.add(failure);
                        }
                    });
                } catch (Exception failure) {
                    tickFailures.add(failure);
                }
            }
        }

        private List<Node> live() {
            return nodes.stream().filter(node -> !node.stopped && !node.raftStopped).toList();
        }

        private Node leader() {
            AtomicReference<Node> selected = new AtomicReference<>();
            await().atMost(BUDGET).until(() -> {
                List<Node> leaders = live().stream().filter(node -> node.raft.getLocalBroker().isLeader()).toList();
                if (leaders.size() != 1) {
                    return false;
                }
                selected.set(leaders.getFirst());
                return true;
            });
            return selected.get();
        }

        private Node verifyHealthyAndLeased() {
            Node leader = awaitInitialLeases(BUDGET, () -> nodes.forEach(Node::assertHealthy), () -> {
                List<Node> leaders = live().stream().filter(node -> node.raft.getLocalBroker().isLeader()).toList();
                return leaders.size() == 1 ? leaders.getFirst() : null;
            }, nodes, (current, node) -> current.app.activateLease(new ActivateRoutingLeaseCommand(
                    "initial-" + node.id, node.id, 0L)), node ->
                    assertThat(node.leases.executeOrElse(new RoutingEpoch(0L), () -> true, false)).isTrue());
            assertThat(leader.raft.getClusterSnapshot().operations()).isEmpty();
            return leader;
        }

        private BrokerClusterOperation awaitAutomaticRemoval(Node failed) {
            AtomicReference<BrokerClusterOperation> found = new AtomicReference<>();
            await().atMost(BUDGET).untilAsserted(() -> {
                List<Node> leaders = live().stream()
                        .filter(node -> node.raft.getLocalBroker().isLeader()).toList();
                assertThat(leaders).as("live Leader while awaiting automatic removal").hasSize(1);
                Node leader = leaders.getFirst();
                List<BrokerClusterOperation> operations = leader.raft.getClusterSnapshot().operations();
                assertThat(operations).as("tick failures: %s", tickFailures).hasSize(1);
                BrokerClusterOperation operation = operations.getFirst();
                assertThat(operation.type()).isEqualTo(BrokerClusterOperationType.FAIL_BROKER);
                assertThat(operation.id().value()).startsWith("auto-failure:");
                assertThat(Duration.between(failed.stoppedAt, operation.createdAt())).isGreaterThanOrEqualTo(STABLE_WINDOW);
                assertThat(operation.targetBrokerId()).isEqualTo(new BrokerId(failed.id));
                assertThat(operation.phase()).isIn(BrokerClusterOperationPhase.REBUILDING_ROUTES,
                        BrokerClusterOperationPhase.VERIFYING, BrokerClusterOperationPhase.DEGRADED);
                found.set(operation);
            });
            return found.get();
        }

        private void assertIsolationAndLeases(BrokerClusterOperation operation, Node failed) throws Exception {
            await().atMost(BUDGET).untilAsserted(() -> {
                for (Node node : live()) {
                    assertThat(node.raft.getCommittedState().epoch()).isEqualTo(1L);
                    assertThat(node.raft.getCommittedState().brokers().stream()
                            .filter(broker -> broker.brokerId().equals(failed.id)).findFirst().orElseThrow().routingState())
                            .isEqualTo(BrokerRoutingState.INACTIVE);
                    assertThat(node.watermark.highestFencedEpoch()).contains(new RoutingEpoch(0L));
                    assertThat(node.leases.executeOrElse(new RoutingEpoch(0L), () -> true, false)).isFalse();
                }
            });
            Node leader = leader();
            assertThat(leader.raft.readLeaderMembershipConfiguration().voters())
                    .as("automatic routing takeover preserves the fixed native voter set")
                    .containsExactlyInAnyOrderElementsOf(voters.stream()
                            .map(MembershipEndpoint::new).toList());
            assertThat(leader.raft.readLeaderMembershipConfiguration().learners()).isEmpty();
            assertThat(live().stream().filter(node -> node != leader).toList()).allSatisfy(node -> {
                assertThat(node.reportCalls.get()).as("real follower report RPCs").isPositive();
                assertThat(node.reportCallers).contains(RoutingHaCertificates.PREFIX + leader.id);
                assertThat(node.stableFailureTargets).as("independent stable failure report over TLS").contains(failed.id);
            });
            for (Node node : live()) {
                // 真实安装之后可能已跨过短测试租约期限；续签仍走 prepare/quorum/grant/install。
                assertThat(leader.app.activateLease(new ActivateRoutingLeaseCommand("verify-" + node.id,
                        node.id, 1L)).toCompletableFuture().get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(node.leases.executeOrElse(new RoutingEpoch(1L), () -> true, false)).isTrue();
            }
            assertThat(failed.leases.executeOrElse(new RoutingEpoch(0L), () -> true, false)).isFalse();
            assertThat(leader.raft.findRoutingFence()).isPresent();
            assertThat(leader.raft.findOperation(operation.id())).isPresent();
        }

        private void acknowledgeGatewayAndAwaitSuccess(BrokerClusterOperation operation) {
            Node leader = leader();
            // 与 GatewayRouteTable 一致，对去重排序后的快照用户集合计算摘要。
            List<Long> snapshotUsers = List.of(1L, 2L, 3L);
            String checksum = HashUtils.sha256HexLongs(snapshotUsers);
            assertThat(leader.app.confirmGatewayRoutes(new ConfirmGatewayRoutesCommand("gateway-fixture", 1L, 10L,
                    (long) snapshotUsers.size(), checksum, Instant.now().plusSeconds(10))).status())
                    .isEqualTo(GatewayRouteSnapshotAckResult.Status.ACKNOWLEDGED);
            await().atMost(BUDGET).untilAsserted(() -> {
                BrokerClusterOperation completed = leader.raft.findOperation(operation.id()).orElseThrow();
                assertThat(completed.phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
                assertThat(completed.drainProgress().gateways()).singleElement().satisfies(checkpoint -> {
                    assertThat(checkpoint.gatewayId().value()).isEqualTo("gateway-fixture");
                    assertThat(checkpoint.confirmedRouteRevision().value()).isEqualTo(10L);
                    assertThat(checkpoint.confirmedRouteCount().value()).isEqualTo(snapshotUsers.size());
                    assertThat(checkpoint.contentChecksum().value()).isEqualTo(checksum);
                });
                assertThat(leader.raft.getClusterSnapshot().operations()).hasSize(1);
            });
        }

        @Override
        public void close() {
            ticks.shutdownNow();
            assertAll("close all Broker nodes", nodes.reversed().stream().map(node -> node::close));
        }
    }

    private static final class Node implements AutoCloseable {
        private final String id;
        private RpcServer server;
        private final List<Channel> acceptedChannels = new CopyOnWriteArrayList<>();
        private BoltControlledClient client;
        private ImBoltProperties.Client clientPolicy;
        private BrokerRaftAdapter raft;
        private BrokerMembershipExecutor membershipExecutor;
        private BrokerMembershipAppService membership;
        private final Set<String> permitConfirmationCallers = ConcurrentHashMap.newKeySet();
        private final List<String> permitRejections = new CopyOnWriteArrayList<>();
        private final ThreadPoolExecutor membershipWorker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
        private final FileFenceWatermarkRepository watermark;
        private final LocalRoutingLeaseService leases;
        private final BrokerClusterAppService app;
        private final BrokerFailureObservationService policy =
                new BrokerFailureObservationService(STABLE_WINDOW, FRESHNESS);
        private Cache<LeaseNonce, PreparedLeaseRequest> pending;
        private final ThreadPoolExecutor peers = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
        private final ScheduledExecutorService quarantine = Executors.newSingleThreadScheduledExecutor();
        private final ThreadPoolExecutor observations = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
        private final AtomicBoolean observing = new AtomicBoolean();
        private final AtomicInteger reportCalls = new AtomicInteger();
        private final Set<String> reportCallers = ConcurrentHashMap.newKeySet();
        private final Set<String> probeCallers = ConcurrentHashMap.newKeySet();
        private final Set<String> stableFailureTargets = ConcurrentHashMap.newKeySet();
        private volatile boolean stopped;
        private volatile Instant stoppedAt;
        private volatile boolean raftStopped;

        private Node(int index, Path directory, List<String> voters, List<String> routes,
                EndpointReservations reservations) throws Exception {
            this(index, directory, voters, routes, reservations, false);
        }

        private Node(int index, Path directory, List<String> voters, List<String> routes,
                EndpointReservations reservations, boolean syncingLearner) throws Exception {
            id = IDS.get(index);
            try {
                BrokerRaftProperties properties = raftProperties(index, directory, voters, routes);
                if (syncingLearner) {
                    properties.setInitialVoters(voters.subList(0, 2));
                    properties.setInitialLearners(List.of(voters.getLast()));
                    BrokerRaftProperties.BootstrapBroker learner = properties.getBootstrapBrokers().getLast();
                    learner.setMembershipRole(com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.LEARNER_SYNCING);
                    learner.setRoutingState(BrokerRoutingState.INACTIVE);
                }
                BrokerClusterState state = properties.initialClusterState(BrokerSelectionAlgorithm.RENDEZVOUS_HASH);
                membershipExecutor = new BrokerMembershipExecutor(new BrokerId(id), membershipWorker);
                if (reservations != null) {
                    reservations.release(voters.get(index));
                }
                raft = new BrokerRaftAdapter(properties, state, new RoutingLeaseGrantService(LEASE), membershipExecutor);
                watermark = new FileFenceWatermarkRepository(directory.resolve(id + "-fence.json"), CLUSTER, id);
                leases = new LocalRoutingLeaseService(new BrokerId(id), () -> raft.getClusterSnapshot().epoch(), watermark);
                pending = CaffeineCacheBuilder.createCaffeineCacheBuilder().limit(16)
                        .expireAfterWrite(10, TimeUnit.SECONDS).buildCache();
                LocalLeaseAppService local = new LocalLeaseAppService(leases, pending);
                configureClient(stores.get(index));
                client.startup();
                BrokerProperties brokerProperties = new BrokerProperties();
                brokerProperties.getPeerCall().setTimeoutMs(500);
                BrokerRoutingProperties routing = new BrokerRoutingProperties();
                BrokerFailureObservationProperties observation = new BrokerFailureObservationProperties();
                observation.setRpcTimeout(RPC_TIMEOUT);
                BrokerPeerAdapter peer = new BrokerPeerAdapter(new BoltClientInvoker(client, System::nanoTime, clientPolicy), brokerProperties,
                        peers, peers, routing, observation, id, observations, client);
                membership = new BrokerMembershipAppService(raft, peer, new MinimumVoterCount(1), membershipExecutor,
                        new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                        new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
                InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
                gateways.register(new GatewayRegistration(new GatewayId("gateway-fixture"), new GatewayHost("127.0.0.1"),
                        new GatewayPort(19001), new GatewayPrincipalId("spiffe://im-chat/gateway/fixture"),
                        Instant.now(), Instant.now()));
                app = createApplication(peer, gateways);
                server = new RpcServer("127.0.0.1", Integer.parseInt(routes.get(index).split(":")[1]));
                configureServer(stores.get(index));
                server.addConnectionEventProcessor(ConnectionEventType.CONNECT,
                        (remoteAddress, connection) -> acceptedChannels.add(connection.getChannel()));
                List<BoltRequestHandler> handlers = requestHandlers(local).stream()
                        .map(this::authenticatedChain).map(this::recordTransportEvidence).toList();
                server.registerUserProcessor(new BoltRequestProcessor(handlers, new ImBoltProperties.Server()));
                if (reservations != null) {
                    reservations.release(routes.get(index));
                }
                try {
                    assertThat(server.start()).isTrue();
                } catch (Exception | AssertionError failure) {
                    throw new IllegalStateException("route server start failed: nodeId=" + id
                            + ", route=" + routes.get(index) + ", pid=" + ProcessHandle.current().pid()
                            + ", mapping=" + IntStream.range(0, IDS.size()).mapToObj(member ->
                                    IDS.get(member) + "[voter=" + voters.get(member)
                                            + ", route=" + routes.get(member) + "]").toList(), failure);
                }
            } catch (Exception | AssertionError failure) {
                try {
                    close();
                } catch (Exception | AssertionError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        private List<AbstractBrokerRpcHandler<?, ?>> requestHandlers(LocalLeaseAppService local) {
            return List.of(new BrokerProbeHandler(app), new BrokerHealthReportHandler(app),
                    new QuorumReadIndexHandler(new BrokerQuorumAppService(raft)),
                    new RouteLeasePrepareHandler(local, id), new RouteLeaseInstallHandler(local),
                    new RoutingFenceHandler(leases, id), new MembershipPermitDrainedHandler(membership, 4, 500));
        }

        private BoltRequestHandler authenticatedChain(AbstractBrokerRpcHandler<?, ?> handler) {
            BrokerCallerIdentityInterceptor identity = new BrokerCallerIdentityInterceptor(Set.of());
            BrokerPeerAdmissionInterceptor admission = new BrokerPeerAdmissionInterceptor(id, raft, CLUSTER,
                    new com.co.kc.imchat.broker.model.properties.BrokerGossipSettings(id, "local:18020",
                            List.of(), List.of(), 1, Duration.ofSeconds(3)));
            return new BrokerRequestInterceptorChain(List.of(identity, admission, new BrokerHandlerInterceptor(handler)));
        }

        private BoltRequestHandler recordTransportEvidence(BoltRequestHandler chain) {
            return new BoltRequestHandler() {
                @Override
                public String service() {
                    return chain.service();
                }

                @Override
                public String operation() {
                    return chain.operation();
                }

                @Override
                public Object handle(BoltRequestContext context, String payload) throws Exception {
                    Object result;
                    try {
                        result = chain.handle(context, payload);
                    } catch (SecurityException failure) {
                        if (chain.operation().equals(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED.operation())) {
                            assertThat(context.tlsVerified()).isTrue();
                            assertThat(context.peerUriIdentities()).hasSize(1);
                            permitRejections.add(failure.getMessage());
                        }
                        throw failure;
                    }
                    assertThat(context.tlsVerified()).isTrue();
                    assertThat(context.peerUriIdentities()).hasSize(1);
                    if (chain.operation().equals(BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT.operation())) {
                        recordReport(context, (BrokerHealthReportResult) result);
                    }
                    if (chain.operation().equals(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED.operation())) {
                        permitConfirmationCallers.addAll(context.peerUriIdentities());
                    }
                    if (chain.operation().equals(BrokerBoltOperation.PROBE_BROKER.operation())) {
                        probeCallers.addAll(context.peerUriIdentities());
                    }
                    return result;
                }
            };
        }

        private MembershipPermitDrainedResult sendPermitConfirmation(Node receiver,
                ConfirmMembershipPermitDrainedParams request) {
            return new BoltClientInvoker(client, System::nanoTime, clientPolicy).invoke(
                    new BoltTarget(receiver.raft.getCommittedState().brokers().stream()
                            .filter(broker -> receiver.id.equals(broker.brokerId())).findFirst().orElseThrow()
                            .routeEndpoint(), RoutingHaCertificates.PREFIX + receiver.id),
                    BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED.service().service(),
                    BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED.operation(), request,
                    MembershipPermitDrainedResult.class, 5000);
        }

        private void recordReport(BoltRequestContext context, BrokerHealthReportResult report) {
            reportCalls.incrementAndGet();
            reportCallers.addAll(context.peerUriIdentities());
            // SDK 与 Domain 均定义 BrokerHealthStatus；此处明确使用 wire 枚举，避免同名 import 冲突。
            report.observations().stream().filter(row -> row.status()
                    == com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNHEALTHY
                    && row.unreachableDurationMs() >= STABLE_WINDOW.toMillis())
                    .map(BrokerHealthReportRow::brokerId).forEach(stableFailureTargets::add);
        }

        private BrokerRaftProperties raftProperties(int index, Path directory, List<String> voters, List<String> routes) {
            BrokerRaftProperties properties = new BrokerRaftProperties();
            properties.setClusterId(CLUSTER);
            properties.setGroupId("routing-ha-" + directory.getFileName());
            properties.setNodeId(id);
            properties.setEndpoint(voters.get(index));
            properties.setInitialVoters(voters);
            properties.setDataDirectory(directory.resolve(id).toString());
            properties.setElectionTimeoutMs(500);
            properties.setSnapshotInterval(Duration.ZERO);
            properties.setBootstrapBrokers(IntStream.range(0, 3).mapToObj(member -> {
                BrokerRaftProperties.BootstrapBroker broker = new BrokerRaftProperties.BootstrapBroker();
                broker.setBrokerId(IDS.get(member));
                broker.setRouteEndpoint(routes.get(member));
                broker.setMembershipEndpoint(voters.get(member));
                return broker;
            }).toList());
            return properties;
        }

        private BrokerClusterAppService createApplication(BrokerPeerAdapter peer, InMemoryGatewayRegistry gateways) {
            BrokerId localId = new BrokerId(id);
            BrokerHealthObservationTracker store = new BrokerHealthObservationTracker(localId, FRESHNESS);
            return new BrokerClusterAppService(raft, peer, gateways, QUARANTINE, Duration.ofMillis(300),
                    quarantine, Duration.ofSeconds(15),
                    store, policy, COOLDOWN);
        }

        private void configureClient(Path store) {
            clientPolicy = new ImBoltProperties.Client();
            clientPolicy.setAllowedPeerUriPrefixes(List.of(RoutingHaCertificates.PREFIX));
            clientPolicy.getTls().setEnabled(true);
            clientPolicy.getTls().setKeyStore(store.toString());
            clientPolicy.getTls().setKeyStorePassword(RoutingHaCertificates.PASSWORD);
            client = new BoltControlledClient(clientPolicy);
        }

        private void configureServer(Path store) {
            server.option(BoltServerOption.SERVER_MANAGE_CONNECTION_SWITCH, true);
            server.option(BoltServerOption.SRV_SSL_ENABLE, true);
            server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
            server.option(BoltServerOption.SRV_SSL_KEYSTORE, store.toString());
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, RoutingHaCertificates.PASSWORD);
        }

        private CompletionStage<Void> observe() {
            if (!observing.compareAndSet(false, true)) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                return app.probeFailures().thenCompose(ignored -> app.reconcileFailures())
                        .whenComplete((ignored, failure) -> observing.set(false));
            } catch (RuntimeException failure) {
                observing.set(false);
                return CompletableFuture.failedFuture(failure);
            }
        }

        private void assertHealthy() {
            BrokerClusterState state = raft.getCommittedState();
            String caller = IDS.stream().filter(identity -> !identity.equals(id)).findFirst().orElseThrow();
            BrokerHealthReportDTO report = app.getHealthReport(new BrokerHealthReportQuery(CLUSTER, caller, id, state.revision(),
                    state.epoch(), "healthy-" + id, IDS));
            assertThat(probeCallers).containsExactlyInAnyOrderElementsOf(IDS.stream()
                    .filter(identity -> !identity.equals(id)).map(identity -> RoutingHaCertificates.PREFIX + identity).toList());
            assertThat(report.observations().stream().filter(row -> !row.brokerId().equals(id)).toList())
                    .hasSize(2).allSatisfy(row -> {
                        assertThat(row.status()).isEqualTo(BrokerHealthStatus.HEALTHY);
                        assertThat(row.unreachableDuration()).isEqualTo(Duration.ZERO);
                        assertThat(row.observationAge()).isLessThan(FRESHNESS);
                    });
        }

        private synchronized void stopRaft() {
            if (!raftStopped) {
                raftStopped = true;
                assertAll("stop Broker membership admission and Raft " + id,
                        () -> { if (membershipExecutor != null) { membershipExecutor.close(); } },
                        () -> {
                            if (raft != null) {
                                try {
                                    raft.close();
                                } catch (InterruptedException failure) {
                                    Thread.currentThread().interrupt();
                                    throw failure;
                                }
                            }
                        });
            }
        }

        private void stopRpcAndAssertClosed() {
            List<Channel> accepted = acceptedChannels.stream().filter(Channel::isActive).toList();
            server.stop();
            assertThat(accepted).as("active accepted channels before stopping %s", id).isNotEmpty();
            assertAll("stopped RPC boundary for " + id,
                    () -> await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                            assertThat(accepted).allSatisfy(channel -> {
                                assertThat(channel.closeFuture().isDone()).isTrue();
                                assertThat(channel.isActive()).isFalse();
                            })),
                    () -> await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                        try (Socket socket = new Socket()) {
                            assertThatThrownBy(() -> socket.connect(
                                    new InetSocketAddress("127.0.0.1", server.port()), 500))
                                    .isInstanceOf(ConnectException.class);
                        }
                    }));
        }

        private synchronized void stop() {
            if (!stopped) {
                stoppedAt = Instant.now();
                stopped = true;
                assertAll("stop Broker " + id, this::stopRpcAndAssertClosed, () -> { if (client != null) { client.shutdown(); } }, this::stopRaft);
            }
        }

        @Override
        public void close() {
            stopped = true;
            assertAll("close Broker " + id,
                    () -> { if (server != null && server.isStarted()) { server.stop(); } },
                    () -> { if (client != null && client.isStarted()) { client.shutdown(); } },
                    this::stopRaft,
                    () -> {
                        membershipWorker.shutdown();
                        peers.shutdownNow();
                        observations.shutdownNow();
                        quarantine.shutdownNow();
                        assertAll("terminate Broker pools", java.util.stream.Stream.of(membershipWorker, peers, observations, quarantine)
                                .map(pool -> () -> {
                                    try {
                                        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                                    } catch (InterruptedException failure) {
                                        Thread.currentThread().interrupt();
                                        throw failure;
                                    }
                                }));
                    },
                    () -> { if (pending != null) { pending.close(); } });
        }

    }

    static final class EndpointReservations implements AutoCloseable {
        private final Map<String, ServerSocket> sockets = new LinkedHashMap<>();
        private final List<String> endpoints;

        EndpointReservations() throws Exception {
            try {
                for (int index = 0; index < 6; index++) {
                    ServerSocket socket = new ServerSocket();
                    try {
                        // 与实际 listener 使用同一显式 IPv4 loopback，避免 wildcard/reuse 的平台差异。
                        socket.bind(new InetSocketAddress("127.0.0.1", 0));
                    } catch (Exception failure) {
                        socket.close();
                        throw failure;
                    }
                    sockets.put("127.0.0.1:" + socket.getLocalPort(), socket);
                }
                endpoints = List.copyOf(sockets.keySet());
            } catch (Exception | AssertionError failure) {
                try {
                    close();
                } catch (Exception | AssertionError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        List<String> endpoints() {
            return endpoints;
        }

        // 仅释放对应 listener 的 reservation；release→bind 仍可能遭遇外部竞争，并非原子移交。
        void release(String endpoint) throws Exception {
            ServerSocket socket = sockets.get(endpoint);
            if (socket == null) {
                throw new IllegalStateException("Endpoint not reserved: " + endpoint);
            }
            socket.close();
            sockets.remove(endpoint);
        }

        @Override
        public void close() {
            assertAll("close unhanded endpoint reservations", sockets.values().stream()
                    .map(socket -> socket::close));
            sockets.clear();
        }
    }
}
