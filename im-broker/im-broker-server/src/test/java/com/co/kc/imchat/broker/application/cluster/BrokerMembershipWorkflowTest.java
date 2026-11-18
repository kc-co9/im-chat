package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;

import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor;

import com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.VoterDemotionIntent;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerMembershipScheduler;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class BrokerMembershipWorkflowTest {
    enum Entry { FIRST_REQUEST, RESUME, SCHEDULER }

    @org.junit.jupiter.api.Test
    void healthyFollowerRejectsWithoutWorkerOrNodeAccess(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        assertFollowerAdmission(directory, "HEALTHY");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CLOSE", "ROLE_FAULT", "FSM_ERROR", "CAPACITY", "GENERATION", "NO_HINT", "PRIORITY", "PUBLICATION_MUTEX"})
    void followerHintNeverOverridesHardRefusal(String boundary,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        assertFollowerAdmission(directory, boundary);
    }

    private void assertFollowerAdmission(java.nio.file.Path directory, String boundary) throws Exception {
        com.alipay.sofa.jraft.Node node = mock(com.alipay.sofa.jraft.Node.class);
        java.util.concurrent.atomic.AtomicReference<com.alipay.sofa.jraft.StateMachine> callback =
                new java.util.concurrent.atomic.AtomicReference<>();
        try (org.mockito.MockedStatic<com.alipay.sofa.jraft.RaftServiceFactory> nodes =
                     org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.RaftServiceFactory.class);
             org.mockito.MockedStatic<com.alipay.sofa.jraft.rpc.RaftRpcServerFactory> servers =
                     org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.rpc.RaftRpcServerFactory.class)) {
            nodes.when(() -> com.alipay.sofa.jraft.RaftServiceFactory.createRaftNode(any(), any())).thenReturn(node);
            when(node.init(any())).thenAnswer(call -> {
                callback.set(((com.alipay.sofa.jraft.option.NodeOptions) call.getArgument(0)).getFsm());
                return true;
            });
            com.alipay.sofa.jraft.rpc.RpcServer rpc = mock(com.alipay.sofa.jraft.rpc.RpcServer.class);
            when(rpc.init(null)).thenReturn(true);
            servers.when(() -> com.alipay.sofa.jraft.rpc.RaftRpcServerFactory.createRaftRpcServer(any(), any(), any()))
                    .thenReturn(rpc);
            java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                    1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                    Thread.ofPlatform().daemon().name("follower-admission-worker").factory(),
                    new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            worker.execute(() -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            });
            try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties properties =
                        new com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties();
                properties.setClusterId("follower-admission");
                properties.setNodeId("a");
                properties.setEndpoint("127.0.0.1:18129");
                properties.setInitialVoters(List.of("127.0.0.1:18129"));
                properties.setDataDirectory(directory.toString());
                com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties.BootstrapBroker bootstrap =
                        new com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties.BootstrapBroker();
                bootstrap.setBrokerId("a");
                bootstrap.setRouteEndpoint("127.0.0.1:18129");
                bootstrap.setMembershipEndpoint("127.0.0.1:18129");
                properties.setBootstrapBrokers(List.of(bootstrap));
                try (BrokerRaftAdapter adapter = org.mockito.Mockito.spy(new BrokerRaftAdapter(properties,
                        properties.initialClusterState(com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm.RENDEZVOUS_HASH),
                        new com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService(Duration.ofSeconds(1)), executor))) {
                    BrokerMembershipAppService service = new BrokerMembershipAppService(adapter, mock(BrokerPeerAdapter.class),
                            new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                            new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
                    BrokerCluster cluster = initialCluster();
                    BrokerClusterOperation operation = cluster.demoteVoter(VoterDemotionIntent.builder()
                            .operationId(new ClusterOperationId("follower-reject")).commandDigest(
                                    new com.co.kc.imchat.broker.domain.cluster.model.CommandDigest("follower-digest"))
                            .expectedRevision(cluster.revision()).brokerId(new BrokerId("b"))
                            .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.EPOCH).build()).operation();
                    when(adapter.getClusterSnapshot()).thenAnswer(ignored -> cluster.snapshot());
                    com.alipay.sofa.jraft.entity.PeerId leader = new com.alipay.sofa.jraft.entity.PeerId();
                    assertThat(leader.parse("127.0.0.1:18130:2:10")).isTrue();
                    if ("CAPACITY".equals(boundary)) {
                        executor.onRoleChanged(7L, 0L);
                        for (int index = 0; index < 4096; index++) {
                            executor.reserve(new ClusterOperationId("budget-" + index), "step", "permit-" + index,
                                    new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                                            java.util.Set.of(new MembershipEndpoint("127.0.0.1:18129")), java.util.Set.of()));
                        }
                        executor.onRoleChanged(0L, 0L);
                    }
                    java.util.concurrent.CompletionStage<?> existing = null;
                    if ("PRIORITY".equals(boundary)) {
                        executor.onRoleChanged(7L, 0L);
                        existing = service.resume(operation.id());
                        assertThat(existing.toCompletableFuture()).isNotDone();
                        executor.onRoleChanged(0L, 0L);
                    }
                    org.mockito.Mockito.clearInvocations(node);
                    callback.get().onStartFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(
                            leader, 7L, new com.alipay.sofa.jraft.Status(com.alipay.sofa.jraft.error.RaftError.ENEWLEADER, "new leader")));
                    java.util.concurrent.atomic.AtomicReference<Thread> rolePublisher = new java.util.concurrent.atomic.AtomicReference<>();
                    switch (boundary) {
                        case "CLOSE" -> executor.close();
                        case "ROLE_FAULT" -> executor.faultLatch().fail(new IllegalStateException("role failure"));
                        case "FSM_ERROR" -> callback.get().onError(new com.alipay.sofa.jraft.error.RaftException());
                        case "GENERATION" -> executor.onRoleChanged(0L, 1L);
                        case "NO_HINT" -> callback.get().onStopFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(
                                leader, 7L, com.alipay.sofa.jraft.Status.OK()));
                        case "PUBLICATION_MUTEX" -> org.mockito.Mockito.doAnswer(invocation -> {
                            Thread publisher = Thread.ofPlatform().daemon().name("following-role-publisher").start(
                                    () -> callback.get().onLeaderStop(com.alipay.sofa.jraft.Status.OK()));
                            rolePublisher.set(publisher);
                            java.lang.management.ThreadMXBean threads = java.lang.management.ManagementFactory.getThreadMXBean();
                            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                            java.lang.management.ThreadInfo blocked = null;
                            while (System.nanoTime() < deadline) {
                                blocked = threads.getThreadInfo(publisher.threadId(), 32);
                                if (blocked != null && blocked.getThreadState() == Thread.State.BLOCKED
                                        && blocked.getLockOwnerId() == Thread.currentThread().threadId()) { break; }
                                Thread.onSpinWait();
                            }
                            assertThat(blocked).isNotNull();
                            assertThat(blocked.getThreadState()).isEqualTo(Thread.State.BLOCKED);
                            assertThat(blocked.getLockOwnerId()).isEqualTo(Thread.currentThread().threadId());
                            assertThat(java.util.Arrays.stream(blocked.getStackTrace()).anyMatch(frame ->
                                    frame.getClassName().equals(BrokerMembershipExecutor.class.getName())
                                            && frame.getMethodName().equals("onRoleChanged"))).isTrue();
                            // 真正的角色 listener 等待当前准入持有的 mutex；getter 不得反锁 rolePublication。
                            Object observed = invocation.callRealMethod();
                            assertThat(observed).isNull();
                            return observed;
                        }).when(adapter).getFollowingObservation();
                        case "HEALTHY", "CAPACITY", "PRIORITY" -> { }
                        default -> throw new AssertionError("Unknown admission boundary");
                    }
                    long taskCount = worker.getTaskCount();
                    int queueSize = worker.getQueue().size();
                    long undrained = executor.undrainedPermitCount();
                    if ("PRIORITY".equals(boundary)) {
                        java.util.concurrent.CompletionStage<?> repeated = service.resume(operation.id());
                        assertThat(repeated.toCompletableFuture()).isNotDone();
                        assertThat(existing.toCompletableFuture()).isNotDone();
                        BrokerClusterOperation conflict = BrokerClusterOperation.begin(
                                com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal.builder()
                                        .operationId(operation.id())
                                        .commandDigest(new com.co.kc.imchat.broker.domain.cluster.model.CommandDigest("different"))
                                        .expectedRevision(operation.acceptedRevision()).operationType(operation.type())
                                        .targetBrokerId(operation.targetBrokerId()).minimumVoterCount(operation.minimumVoterCount())
                                        .changedAt(Instant.EPOCH).build(), operation.acceptedRevision(), operation.acceptedEpoch());
                        BrokerCluster conflictingSnapshot = initialCluster();
                        when(adapter.getClusterSnapshot()).thenReturn(spy(conflictingSnapshot));
                        when(adapter.getClusterSnapshot().findOperation(operation.id())).thenReturn(java.util.Optional.of(conflict));
                        assertThat(((BrokerClusterConsensusResult) service.resume(operation.id()).toCompletableFuture()
                                .get(5, java.util.concurrent.TimeUnit.SECONDS)).status())
                                .isEqualTo(BrokerClusterConsensusResult.Status.REJECTED);
                    }
                    for (int request = 0; request < ("PRIORITY".equals(boundary) ? 0 : "PUBLICATION_MUTEX".equals(boundary) ? 1 : 3); request++) {
                        BrokerClusterConsensusResult result = (BrokerClusterConsensusResult) service.resume(operation.id())
                                .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                        assertThat(result.status()).isEqualTo("HEALTHY".equals(boundary)
                                ? BrokerClusterConsensusResult.Status.NOT_LEADER : BrokerClusterConsensusResult.Status.UNAVAILABLE);
                        if ("HEALTHY".equals(boundary)) {
                            assertThat(result.leaderEndpoint()).isEqualTo(leader.toString());
                        } else {
                            assertThat(result.leaderEndpoint()).isNull();
                        }
                    }
                    if (rolePublisher.get() != null) {
                        rolePublisher.get().join(java.util.concurrent.TimeUnit.SECONDS.toMillis(5));
                        assertThat(rolePublisher.get().isAlive()).isFalse();
                    }
                    assertThat(worker.getQueue()).hasSize(queueSize);
                    assertThat(worker.getTaskCount()).isEqualTo(taskCount);
                    if ("HEALTHY".equals(boundary)) {
                        assertThat(queueSize).isZero();
                        assertThat(taskCount).isEqualTo(1L);
                    }
                    assertThat(executor.nativeBusyCount()).isZero();
                    assertThat(executor.undrainedPermitCount()).isEqualTo(undrained);
                    org.mockito.Mockito.verifyNoInteractions(node);
                    if ("HEALTHY".equals(boundary)) {
                        executor.onRoleChanged(7L, 0L);
                        com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership target =
                                new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                                        java.util.Set.of(new MembershipEndpoint("127.0.0.1:18129")), java.util.Set.of());
                        for (int index = 0; index < 4096; index++) {
                            executor.reserve(new ClusterOperationId("remaining-" + index), "step", "remaining-" + index, target);
                        }
                        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.reserve(
                                new ClusterOperationId("overflow"), "step", "overflow", target))
                                .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                    }
                }
            } finally {
                release.countDown();
                worker.shutdownNow();
                assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @ParameterizedTest(name = "{0} must use the persisted permit entry, never native delta")
    @EnumSource(Entry.class)
    void firstResumeAndSchedulerMustUseTheSamePersistedPermitEntry(Entry entry) {
        DemoteVoterCommand command = new DemoteVoterCommand("demote", "b", 0L, "b:18120");
        BrokerCluster cluster = initialCluster();
        BrokerClusterOperation operation = cluster.demoteVoter(VoterDemotionIntent.builder()
                .operationId(new ClusterOperationId(command.operationId())).commandDigest(command.digest())
                .expectedRevision(cluster.revision()).brokerId(new BrokerId("b"))
                .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.EPOCH).build()).operation();
        BrokerCluster requestSnapshot = entry == Entry.FIRST_REQUEST ? initialCluster() : cluster;
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.isLeader()).thenReturn(true);
        when(adapter.getClusterSnapshot()).thenAnswer(ignored -> requestSnapshot.snapshot());
        when(adapter.beginOperation(any())).thenReturn(
                CompletableFuture.completedFuture(BrokerClusterConsensusResult.applied(operation)));
        BrokerMembershipExecutor executor = mock(BrokerMembershipExecutor.class);
        BrokerMembershipAppService service = new BrokerMembershipAppService(adapter,
                mock(BrokerPeerAdapter.class), new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
        when(executor.submitApplicationStage(any(), any(), any())).thenReturn(
                new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult(
                        com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult.Status.UNAVAILABLE, null, "controlled permit boundary"));

        switch (entry) {
            case FIRST_REQUEST -> service.demoteVoter(command).toCompletableFuture().join();
            case RESUME -> service.resume(operation.id()).toCompletableFuture().join();
            case SCHEDULER -> new BrokerMembershipScheduler(adapter, service, Duration.ZERO).reconcile();
        }

        org.mockito.Mockito.verify(executor, org.mockito.Mockito.times(1)).submitApplicationStage(any(), any(), any());
        org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).changeMembershipConfiguration(any());
    }

    @org.junit.jupiter.api.Test
    void freshNativeRequestMustFailClosedWhenConfigurationReadIsUnavailable() throws Exception {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.isLeader()).thenReturn(true);
        when(local.getId()).thenReturn(new BrokerId("a"));
        when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(
                new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L)));
        when(adapter.getClusterSnapshot()).thenAnswer(ignored -> initialCluster());
        when(adapter.readLeaderMembershipConfiguration()).thenThrow(
                new IllegalStateException("controlled configuration read failure"));
        java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("t5-drift-worker").factory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(7L, 1L);
            BrokerMembershipAppService service = new BrokerMembershipAppService(adapter, mock(BrokerPeerAdapter.class),
                    new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                    new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
            DemoteVoterCommand command = new DemoteVoterCommand("drift", "b", 0L, "b:18120");
            assertThat(service.demoteVoter(command).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo("UNAVAILABLE");
            assertThat(service.demoteVoter(command).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo("UNAVAILABLE");
            org.mockito.Mockito.verify(adapter, org.mockito.Mockito.times(2)).readLeaderMembershipConfiguration();
            org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).beginOperation(any());
            org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).changeMembershipConfiguration(any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Entry.class, names = {"RESUME", "SCHEDULER"})
    void raftCommittedLearnerResumeQueuesTerminalOnlyOnWorker(Entry entry) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            fixture.blockWorker();
            fixture.recover(entry);
            assertThat(fixture.terminalSubmits.get()).as("caller must not submit while worker is paused").isZero();
            assertThat(fixture.machine.getCluster().findObservers()).as("READY alone must not release D10").isEmpty();
            fixture.release.countDown();
            assertThat(fixture.finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.submitThread).isEqualTo("t5-membership-worker");
            assertThat(fixture.terminalSubmits.get()).isEqualTo(1);
            assertThat(fixture.machine.findOperation(fixture.operation.id()).orElseThrow().phase())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.SUCCEEDED);
            assertThat(fixture.machine.getCluster().findObservers()).isNotEmpty();
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).beginOperation(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).commitOperation(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).confirmLeadership(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).registerMembershipPermit(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).changeMembershipConfiguration(any());
        }
    }

    enum Stop { SAME_TERM_RESTART, FAULT, CLOSE }

    @org.junit.jupiter.api.Test
    void raftCommittedLearnerMustRecheckLeaderTermBeforeTerminalSubmit() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            fixture.pauseWorkerSnapshot = true;
            fixture.service.resume(fixture.operation.id());
            assertThat(fixture.snapshotEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            when(fixture.adapter.getLocalBroker().getLeaderTerm()).thenReturn(
                    java.util.Optional.of(new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(8L)));
            fixture.release.countDown();
            fixture.worker.shutdown();
            assertThat(fixture.worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.terminalSubmits.get()).as("term changed before terminal worker stage").isZero();
            assertThat(fixture.machine.getCluster().findObservers()).isEmpty();
        }
    }


    @ParameterizedTest
    @EnumSource(Stop.class)
    void raftCommittedLearnerRecoveryCannotSubmitAfterRoleRestartFaultOrClose(Stop stop) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            fixture.blockWorker();
            fixture.recover(Entry.RESUME);
            assertThat(fixture.terminalSubmits.get()).as("recovery must first enter bounded worker").isZero();
            switch (stop) {
                case SAME_TERM_RESTART -> {
                    fixture.executor.onRoleChanged(0L, 2L);
                    fixture.executor.onRoleChanged(7L, 3L);
                }
                case FAULT -> fixture.executor.faultLatch().fail(new IllegalStateException("controlled fault"));
                case CLOSE -> fixture.executor.close();
            }
            fixture.release.countDown();
            fixture.worker.shutdown();
            assertThat(fixture.worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.terminalSubmits.get()).as("invalidated attempt must never submit or fallback").isZero();
            assertThat(fixture.machine.findOperation(fixture.operation.id()).orElseThrow().phase())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.RAFT_COMMITTED);
            assertThat(fixture.machine.getCluster().findObservers()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(Stop.class)
    void raftCommittedLearnerLateTerminalContinuationCannotSubmitAfterRoleRestartFaultOrClose(Stop stop) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            fixture.pauseWorkerSnapshot = true;
            fixture.service.resume(fixture.operation.id());
            assertThat(fixture.snapshotEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            switch (stop) {
                case SAME_TERM_RESTART -> {
                    fixture.executor.onRoleChanged(0L, 2L);
                    fixture.executor.onRoleChanged(7L, 3L);
                }
                case FAULT -> fixture.executor.faultLatch().fail(new IllegalStateException("controlled late fault"));
                case CLOSE -> fixture.executor.close();
            }
            fixture.release.countDown();
            fixture.worker.shutdown();
            assertThat(fixture.worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.terminalSubmits.get()).isZero();
            assertThat(fixture.machine.getCluster().findObservers()).isEmpty();
        }
    }

    @org.junit.jupiter.api.Test
    void learnerWorkflowAndNativePermitsSharePermanent4096Budget() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership target =
                    new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                            java.util.Set.of(new MembershipEndpoint("a:18120")), java.util.Set.of());
            for (int index = 0; index < 4095; index++) {
                fixture.executor.reserve(new ClusterOperationId("reserved-" + index), "step", "permit-" + index, target);
            }
            fixture.blockWorker();
            fixture.service.resume(fixture.operation.id());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.executor.reserve(
                    new ClusterOperationId("overflow"), "step", "overflow", target))
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult overflow = fixture.service.confirmLearner(
                    new com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand("other", "b", 0L, "b:18120"))
                    .toCompletableFuture().join();
            assertThat(overflow.status()).isEqualTo(BrokerClusterConsensusResult.Status.UNAVAILABLE);
            fixture.release.countDown();
            assertThat(fixture.finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.executor.reserve(
                    new ClusterOperationId("after-completion"), "step", "after-completion", target))
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        }
    }

    @org.junit.jupiter.api.Test
    void unknownLearnerAttemptCannotRetryWhileOldAsyncStageIsStillOutstanding() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            CompletableFuture<BrokerClusterConsensusResult> lateSubmit = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch submitted = new java.util.concurrent.CountDownLatch(1);
            doAnswer(ignored -> {
                fixture.terminalSubmits.incrementAndGet();
                submitted.countDown();
                return lateSubmit;
            }).when(fixture.adapter).completeMembershipOperation(any());
            com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand command =
                    new com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand("sync", "b", 0L, "b:18120");
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> first =
                    fixture.service.resume(fixture.operation.id()).thenApply(result ->
                            (com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult) result);
            assertThat(submitted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            fixture.executor.onRoleChanged(0L, 2L);
            fixture.executor.onRoleChanged(7L, 3L);
            // 同事实重投立即报告在途；不能因换届把未返回的 submit 当作排空。
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> duplicate =
                    fixture.service.confirmLearner(command);
            java.util.concurrent.CountDownLatch workerObserved = new java.util.concurrent.CountDownLatch(1);
            fixture.worker.execute(workerObserved::countDown);
            assertThat(workerObserved.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.terminalSubmits.get()).isEqualTo(1);
            assertThat(first.toCompletableFuture().isDone()).isFalse();
            assertThat(duplicate.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status().name())
                    .isEqualTo("IN_PROGRESS");
            lateSubmit.complete(BrokerClusterConsensusResult.unavailable(null, "controlled unknown"));
        }
    }

    @org.junit.jupiter.api.Test
    void freshLearnerConfirmationObservesLeaderConfigurationBeforeEvidenceAndBegin() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            fixture.service.confirmLearner(fixture.command()).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            org.mockito.InOrder order = org.mockito.Mockito.inOrder(fixture.adapter, fixture.peer);
            order.verify(fixture.adapter).readLeaderMembershipConfiguration();
            order.verify(fixture.adapter).confirmLeadership(any());
            order.verify(fixture.peer).fetchQuorumReadIndex(any());
            order.verify(fixture.adapter).beginOperation(any());
            assertThat(fixture.beginSubmits.get()).isEqualTo(1);
        }
    }

    @org.junit.jupiter.api.Test
    void missingLearnerInLeaderObservationFailsClosedBeforeProgressOrBegin() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            when(fixture.adapter.readLeaderMembershipConfiguration()).thenReturn(
                    new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                            java.util.Set.of(new MembershipEndpoint("a:18120")), java.util.Set.of()));
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status().name()).isEqualTo("REJECTED");
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).confirmLeadership(any());
            org.mockito.Mockito.verifyNoInteractions(fixture.peer);
            assertThat(fixture.beginSubmits.get()).isZero();
            assertThat(fixture.commitSubmits.get()).isZero();
        }
    }

    enum EvidenceChange { TERM, READ_TERM, GENERATION, REVISION, ENDPOINT }

    @ParameterizedTest
    @EnumSource(EvidenceChange.class)
    void freshLearnerEvidenceMustBeRevalidatedBeforeBegin(EvidenceChange change) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<Long> progress = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(ignored -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-membership-worker");
                requested.countDown(); return progress;
            });
            if (change == EvidenceChange.READ_TERM) {
                when(fixture.adapter.confirmLeadership(any())).thenReturn(CompletableFuture.completedFuture(
                        new com.co.kc.imchat.broker.domain.authority.model.QuorumRead(
                                new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(8L),
                                new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L))));
            }
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> result =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            switch (change) {
                case TERM -> when(fixture.adapter.getLocalBroker().getLeaderTerm()).thenReturn(
                        java.util.Optional.of(new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(8L)));
                case GENERATION -> { fixture.executor.onRoleChanged(0L, 2L); fixture.executor.onRoleChanged(7L, 3L); }
                case REVISION, ENDPOINT -> {
                    BrokerCluster before = fixture.machine.getCluster();
                    fixture.snapshotOverride = BrokerCluster.builder().revision(change == EvidenceChange.REVISION
                                    ? before.revision().next() : before.revision()).epoch(before.epoch())
                            .algorithm(before.algorithm()).brokers(before.brokers()).operations(before.operations())
                            .membershipEndpoints(change == EvidenceChange.ENDPOINT
                                    ? Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"),
                                            new BrokerId("b"), new MembershipEndpoint("changed:18120")) : before.membershipEndpoints())
                            .operationHistoryCapacity(new OperationHistoryCapacity(100)).build();
                }
                case READ_TERM -> { }
            }
            Thread callback = Thread.ofPlatform().name("t5-peer-read-callback").start(() -> progress.complete(10L));
            callback.join(5000L);
            assertThat(result.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isNotEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
            assertThat(fixture.beginSubmits.get()).as("changed evidence must not register Begin").isZero();
            assertThat(fixture.machine.findOperation(new ClusterOperationId("sync"))).isEmpty();
        }
    }

    @org.junit.jupiter.api.Test
    void concurrentLearnerRequestsReportInProgressWithoutRepeatingExecution() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<Long> progress = new CompletableFuture<>();
            AtomicInteger reads = new AtomicInteger();
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(ignored -> {
                reads.incrementAndGet();
                return progress;
            });
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            try (java.util.concurrent.ExecutorService callers = java.util.concurrent.Executors.newFixedThreadPool(8)) {
                java.util.List<java.util.concurrent.Future<java.util.concurrent.CompletionStage<
                        com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult>>> requests =
                        new java.util.ArrayList<>();
                for (int index = 0; index < 8; index++) {
                    requests.add(callers.submit(() -> {
                        assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                        return fixture.service.confirmLearner(fixture.command());
                    }));
                }
                start.countDown();
                for (java.util.concurrent.Future<?> request : requests) {
                    request.get(5, java.util.concurrent.TimeUnit.SECONDS);
                }
                progress.complete(10L);
                int applied = 0;
                int inProgress = 0;
                for (java.util.concurrent.Future<?> request : requests) {
                    java.util.concurrent.CompletionStage<?> view = (java.util.concurrent.CompletionStage<?>) request.get();
                    com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult result =
                            (com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult)
                                    view.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (result.status() == BrokerClusterConsensusResult.Status.APPLIED) {
                        applied++;
                    } else {
                        assertThat(result.status().name()).isEqualTo("IN_PROGRESS");
                        assertThat(result.acceptedEpoch()).isNull();
                        assertThat(result.targetEpoch()).isNull();
                        inProgress++;
                    }
                }
                assertThat(applied).isEqualTo(1);
                assertThat(inProgress).isEqualTo(7);
                assertThat(reads.get()).isEqualTo(1);
                assertThat(fixture.beginSubmits.get()).isEqualTo(1);
                assertThat(fixture.commitSubmits.get()).isEqualTo(1);
            }
        }
    }

    @org.junit.jupiter.api.Test
    void identicalLearnerRequestsReportInProgressAndCancellationCannotAffectOwner() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<Long> progress = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
            AtomicInteger reads = new AtomicInteger();
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(ignored -> {
                reads.incrementAndGet(); requested.countDown(); return progress;
            });
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> first =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            java.util.concurrent.CompletableFuture<?> cancelled = first.toCompletableFuture();
            assertThat(cancelled.cancel(false)).isTrue();
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> shared =
                    fixture.service.confirmLearner(fixture.command());
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> conflict =
                    fixture.service.confirmLearner(new com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand(
                            "sync", "b", 0L, "forged:18120"));
            assertThat(conflict.toCompletableFuture()).isCompleted();
            progress.complete(10L);
            assertThat(conflict.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.REJECTED);
            assertThat(shared.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status().name())
                    .isEqualTo("IN_PROGRESS");
            assertThat(first.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
            assertThat(reads.get()).isEqualTo(1);
            assertThat(fixture.beginSubmits.get()).isEqualTo(1);
            assertThat(fixture.commitSubmits.get()).isEqualTo(1);
            assertThat(fixture.machine.findOperation(new ClusterOperationId("sync")).orElseThrow().acceptedRevision())
                    .isEqualTo(new ClusterRevision(0L));
        }
    }

    @org.junit.jupiter.api.Test
    void exceptionalLearnerAttemptOnlyRetriesOnExplicitSameFactAfterUnknownTerminal() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            AtomicInteger reads = new AtomicInteger();
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(ignored -> reads.incrementAndGet() == 1
                    ? CompletableFuture.failedFuture(new IllegalStateException("controlled peer failure"))
                    : CompletableFuture.completedFuture(10L));
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture().get(5,
                    java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(BrokerClusterConsensusResult.Status.UNAVAILABLE);
            assertThat(fixture.beginSubmits.get()).isZero();
            assertThat(reads.get()).isEqualTo(1);
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture().get(5,
                    java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
            assertThat(reads.get()).isEqualTo(2);
            assertThat(fixture.beginSubmits.get()).isEqualTo(1);
        }
    }

    @org.junit.jupiter.api.Test
    void synchronousLearnerFailurePublishesUnknownOnWorkerAndAllowsExplicitRecovery() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            AtomicInteger calls = new AtomicInteger();
            doAnswer(ignored -> {
                if (Thread.currentThread().getName().equals("t5-membership-worker") && calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("controlled synchronous failure");
                }
                return fixture.machine.getCluster();
            }).when(fixture.adapter).getClusterSnapshot();
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(BrokerClusterConsensusResult.Status.UNAVAILABLE);
            assertThat(fixture.terminalSubmits.get()).isZero();
            fixture.service.resume(fixture.operation.id()).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fixture.machine.findOperation(fixture.operation.id()).orElseThrow().phase())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.SUCCEEDED);
            assertThat(fixture.terminalSubmits.get()).isEqualTo(1);
        }
    }

    @org.junit.jupiter.api.Test
    void unknownTerminalSubmitRequiresExplicitReplayWithoutRepeatingLearnerEvidenceOrBegin() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            AtomicInteger calls = new AtomicInteger();
            doAnswer(invocation -> {
                calls.incrementAndGet();
                if (calls.get() == 1) {
                    return CompletableFuture.completedFuture(BrokerClusterConsensusResult.unavailable(null, "controlled unknown submit"));
                }
                return CompletableFuture.completedFuture(fixture.machine.apply(
                        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                                .membershipCompletionCommandFrom(invocation.getArgument(0), Instant.now())));
            }).when(fixture.adapter).completeMembershipOperation(any());
            assertThat(((com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult)
                    fixture.service.resume(fixture.operation.id()).toCompletableFuture()
                            .get(5, java.util.concurrent.TimeUnit.SECONDS)).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.UNAVAILABLE);
            assertThat(calls.get()).isEqualTo(1);
            assertThat(fixture.machine.getCluster().findObservers()).isEmpty();
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(BrokerClusterConsensusResult.Status.DUPLICATE);
            assertThat(calls.get()).isEqualTo(1);
            fixture.service.resume(fixture.operation.id()).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(2);
            assertThat(fixture.beginSubmits.get()).isZero();
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).confirmLeadership(any());
        }
    }

    @org.junit.jupiter.api.Test
    void saturatedPendingLearnerFactIsWokenByAcceptedNativeExitWithoutCallbackFallback() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<Long> progress = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(ignored -> { requested.countDown(); return progress; });
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> result =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            java.util.concurrent.CountDownLatch nativeEntered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch nativeRelease = new java.util.concurrent.CountDownLatch(1);
            com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership target =
                    new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                            java.util.Set.of(new MembershipEndpoint("a:18120")), java.util.Set.of());
            fixture.executor.reserve(new ClusterOperationId("held"), "step", "held", target);
            fixture.executor.reserve(new ClusterOperationId("queued"), "step", "queued", target);
            com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit held =
                    new com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit("held", new ClusterOperationId("held"),
                            "step", target, new BrokerId("a"), fixture.executor.incarnation(),
                            com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit.Result.UNKNOWN);
            com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit queued =
                    new com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit("queued", new ClusterOperationId("queued"),
                            "step", target, new BrokerId("a"), fixture.executor.incarnation(),
                            com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit.Result.UNKNOWN);
            try {
                fixture.executor.execute(held, () -> {
                    nativeEntered.countDown();
                    try { assertThat(nativeRelease.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException failure) { throw new IllegalStateException(failure); }
                    return CompletableFuture.completedFuture(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                            com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED, 0, null, null));
                });
                assertThat(nativeEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                fixture.executor.execute(queued, () -> CompletableFuture.completedFuture(
                        new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                                com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED, 0, null, null)));
                assertThat(fixture.worker.getQueue()).hasSize(1);
                Thread callback = Thread.ofPlatform().name("t5-saturated-peer-callback").start(() -> progress.complete(10L));
                callback.join(5000L);
                assertThat(callback.isAlive()).isFalse();
                assertThat(fixture.beginSubmits.get()).isZero();
                assertThat(result.toCompletableFuture().isDone()).isFalse();
            } finally { nativeRelease.countDown(); }
            assertThat(result.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
            assertThat(fixture.beginSubmits.get()).isEqualTo(1);
            assertThat(fixture.submitThread).isEqualTo("t5-membership-worker");
        }
    }

    enum LateTermStage { BEGIN_RESULT, COMMIT_RESULT }

    @ParameterizedTest
    @EnumSource(LateTermStage.class)
    void learnerLateConsensusCallbackMustRecheckTermBeforeNextSubmit(LateTermStage stage) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<BrokerClusterConsensusResult> response = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch applied = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<BrokerClusterConsensusResult> fact = new java.util.concurrent.atomic.AtomicReference<>();
            org.mockito.stubbing.Answer<CompletableFuture<BrokerClusterConsensusResult>> answer = invocation -> {
                BrokerClusterConsensusResult result;
                if (stage == LateTermStage.BEGIN_RESULT) {
                    fixture.beginSubmits.incrementAndGet();
                    result = fixture.machine.apply(com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                            .beginCommandFrom(invocation.getArgument(0)));
                } else {
                    fixture.commitSubmits.incrementAndGet();
                    result = fixture.machine.apply(com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                            .commitCommandFrom(invocation.getArgument(0), Instant.now()));
                }
                fact.set(result); applied.countDown(); return response;
            };
            if (stage == LateTermStage.BEGIN_RESULT) { doAnswer(answer).when(fixture.adapter).beginOperation(any()); }
            else { doAnswer(answer).when(fixture.adapter).commitOperation(any()); }
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> result =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(applied.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            when(fixture.adapter.getLocalBroker().getLeaderTerm()).thenReturn(java.util.Optional.of(
                    new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(8L)));
            Thread callback = Thread.ofPlatform().name("t5-consensus-result-callback").start(() -> response.complete(fact.get()));
            callback.join(5000L);
            assertThat(result.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.NOT_LEADER);
            if (stage == LateTermStage.BEGIN_RESULT) { assertThat(fixture.commitSubmits.get()).isZero(); }
            assertThat(fixture.terminalSubmits.get()).isZero();
            assertThat(fixture.machine.getCluster().findObservers()).isEmpty();
        }
    }

    @org.junit.jupiter.api.Test
    void permitAppliedIndexMappingMustNeverUseDefaultAsyncExecutor() throws Exception {
        BrokerCluster cluster = initialCluster();
        BrokerClusterOperation operation = cluster.demoteVoter(VoterDemotionIntent.builder()
                .operationId(new ClusterOperationId("permit-thread")).commandDigest(new com.co.kc.imchat.broker.domain.cluster.model.CommandDigest("digest"))
                .expectedRevision(cluster.revision()).brokerId(new BrokerId("b"))
                .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.EPOCH).build()).operation();
        com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipStep step = operation.currentMembershipStep();
        assertThat(cluster.registerMembershipPermit(operation.id(), step.stepId(), "thread-permit", new BrokerId("a"),
                "thread-inc", cluster.revision()).accepted()).isTrue();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.getId()).thenReturn(new BrokerId("a"));
        when(local.isLeader()).thenReturn(true);
        when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L)));
        when(adapter.getClusterId()).thenReturn(new com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId("cluster-a"));
        when(adapter.getClusterSnapshot()).thenAnswer(ignored -> cluster.snapshot());
        when(adapter.isMembershipPermitDrainCurrent(any())).thenReturn(true);
        when(adapter.confirmMembershipPermitDrain(any())).thenReturn(CompletableFuture.completedFuture(
                new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(
                        new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L), 1L,
                        new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L))));
        AtomicInteger defaultAsyncCalls = new AtomicInteger();
        when(adapter.fetchQuorumReadIndex()).thenAnswer(ignored -> {
            CompletableFuture<Long> read = new CompletableFuture<>() {
                @Override
                public java.util.concurrent.Executor defaultExecutor() {
                    defaultAsyncCalls.incrementAndGet();
                    return super.defaultExecutor();
                }
            };
            read.complete(10L);
            return read;
        });
        when(adapter.confirmMembershipPermitDrained(any())).thenReturn(
                CompletableFuture.completedFuture(BrokerClusterConsensusResult.applied(operation)));
        java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L,
                java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("t5-permit-proof-worker").factory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(7L, 1L);
            BrokerMembershipAppService service = new BrokerMembershipAppService(adapter, mock(BrokerPeerAdapter.class),
                    new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                    new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
            com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams request =
                    new com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams("cluster-a", "a", "a",
                            operation.id().value(), step.stepId(), "thread-permit", "a", "thread-inc", step.targetDigest(),
                            com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams.Result.TARGET_APPLIED);
            assertThat(service.confirmExecution(acceptedFact(request, "a")).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED);
            assertThat(defaultAsyncCalls.get()).as("permit proof must not select common pool or default async executor").isZero();
        } finally { worker.shutdown(); assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest
    @EnumSource(value = Entry.class, names = {"RESUME", "SCHEDULER"})
    void persistedPendingLearnerUnknownBeginResponseRecoversWithoutReplacementEvidence(Entry entry) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            AtomicInteger begins = new AtomicInteger();
            doAnswer(invocation -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-membership-worker");
                BrokerClusterConsensusResult applied = fixture.machine.apply(
                        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                                .beginCommandFrom(invocation.getArgument(0)));
                return CompletableFuture.completedFuture(begins.incrementAndGet() == 1
                        ? BrokerClusterConsensusResult.unavailable(null, "original Begin response lost after apply") : applied);
            }).when(fixture.adapter).beginOperation(any());
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(BrokerClusterConsensusResult.Status.UNAVAILABLE);
            assertThat(fixture.machine.findOperation(fixture.operation.id()).orElseThrow().phase())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.PENDING);
            org.mockito.Mockito.clearInvocations(fixture.adapter, fixture.peer);
            fixture.blockWorker();
            fixture.recover(entry);
            assertThat(fixture.commitSubmits.get()).isZero();
            assertThat(fixture.machine.getCluster().findObservers()).isEmpty();
            fixture.release.countDown();
            assertThat(fixture.finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.machine.findOperation(fixture.operation.id()).orElseThrow().acceptedRevision())
                    .isEqualTo(new ClusterRevision(0L));
            assertThat(fixture.commitSubmits.get()).isEqualTo(1);
            assertThat(fixture.machine.getCluster().findObservers()).isNotEmpty();
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).confirmLeadership(any());
            org.mockito.Mockito.verify(fixture.peer, org.mockito.Mockito.never()).fetchQuorumReadIndex(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).registerMembershipPermit(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).changeMembershipConfiguration(any());
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void asynchronousQuorumReadCallbackOnlyPostsFactsUntilWorkerConsumes(boolean invalidate) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<com.co.kc.imchat.broker.domain.authority.model.QuorumRead> read = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
            when(fixture.adapter.confirmLeadership(any())).thenAnswer(ignored -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-membership-worker");
                requested.countDown(); return read;
            });
            BrokerRaftAdapter.LocalBroker local = fixture.adapter.getLocalBroker();
            doAnswer(ignored -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-membership-worker");
                return true;
            }).when(local).isLeader();
            doAnswer(ignored -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-membership-worker");
                return java.util.Optional.of(new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L));
            }).when(local).getLeaderTerm();
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(ignored -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-membership-worker");
                return CompletableFuture.completedFuture(10L);
            });
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> result =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            fixture.blockWorker();
            Thread callback = Thread.ofPlatform().name("t5-quorum-read-callback").start(() -> read.complete(
                    new com.co.kc.imchat.broker.domain.authority.model.QuorumRead(
                            new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L),
                            new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L))));
            callback.join(5000L);
            assertThat(callback.isAlive()).isFalse();
            org.mockito.Mockito.verify(fixture.peer, org.mockito.Mockito.never()).fetchQuorumReadIndex(any());
            assertThat(fixture.beginSubmits.get()).isZero();
            if (invalidate) { fixture.executor.onRoleChanged(0L, 2L); fixture.executor.onRoleChanged(7L, 3L); }
            fixture.release.countDown();
            assertThat(result.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(invalidate ? BrokerClusterConsensusResult.Status.NOT_LEADER : BrokerClusterConsensusResult.Status.APPLIED);
            if (invalidate) {
                org.mockito.Mockito.verify(fixture.peer, org.mockito.Mockito.never()).fetchQuorumReadIndex(any());
                assertThat(fixture.beginSubmits.get()).isZero();
            } else {
                assertThat(fixture.beginSubmits.get()).isEqualTo(1);
                assertThat(fixture.commitSubmits.get()).isEqualTo(1);
                assertThat(fixture.submitThread).isEqualTo("t5-membership-worker");
            }
        }
    }

    @org.junit.jupiter.api.Test
    void repeatedLearnerRequestsCompleteImmediatelyWhileOwnerCancellationStaysIsolated() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<Long> progress = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(ignored -> { requested.countDown(); return progress; });
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> first =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(first.toCompletableFuture().cancel(false)).isTrue();
            for (int index = 0; index < 128; index++) {
                java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> duplicate =
                        fixture.service.confirmLearner(fixture.command());
                assertThat(duplicate.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status().name())
                        .isEqualTo("IN_PROGRESS");
                assertThat(duplicate.toCompletableFuture().get().acceptedEpoch()).isNull();
                assertThat(first.toCompletableFuture().isDone()).isFalse();
            }
            progress.complete(10L);

            assertThat(first.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
            assertThat(fixture.beginSubmits.get()).isEqualTo(1);
            assertThat(fixture.commitSubmits.get()).isEqualTo(1);
        }
    }

    enum NativeOutcome { APPLIED, UNKNOWN, ROLE_RESTART }

    @ParameterizedTest
    @ValueSource(strings = {"FAULT", "CLOSE"})
    void delayedBeginDoesNotPublishOnCallbackWhenWorkerUnavailable(String boundary) throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            CompletableFuture<BrokerClusterConsensusResult> delayedBegin = new CompletableFuture<>();
            CompletableFuture<BrokerClusterConsensusResult> committedBegin = new CompletableFuture<>();
            doAnswer(call -> {
                BrokerClusterOperation operation = call.getArgument(0);
                BrokerClusterConsensusResult committed = fixture.machine.apply(
                        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                                .beginCommandFrom(operation));
                committedBegin.complete(committed);
                return delayedBegin;
            }).when(fixture.adapter).beginOperation(any());
            java.util.concurrent.CompletionStage<?> result = fixture.service.demoteVoter(
                    new DemoteVoterCommand("delayed-closed-begin", "b", 0L, "b:18120"));
            BrokerClusterConsensusResult committed = committedBegin.get(5, java.util.concurrent.TimeUnit.SECONDS);
            if (boundary.equals("FAULT")) {
                fixture.executor.faultLatch().fail(new IllegalStateException("controlled fault"));
            } else {
                fixture.executor.close();
            }
            Thread callback = Thread.ofPlatform().name("controlled-begin-callback")
                    .start(() -> delayedBegin.complete(committed));
            callback.join(5000L);
            assertThat(callback.isAlive()).isFalse();
            assertThat(result.toCompletableFuture()).isNotDone();
            assertThat(fixture.targets).isEmpty();
            assertThat(fixture.commits).hasValue(0);
        }
    }

    @org.junit.jupiter.api.Test
    void delayedBeginCannotReservePermitAfterSameTermRoleRestart() throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            CompletableFuture<BrokerClusterConsensusResult> delayedBegin = new CompletableFuture<>();
            CompletableFuture<BrokerClusterConsensusResult> committedBegin = new CompletableFuture<>();
            doAnswer(call -> {
                BrokerClusterOperation operation = call.getArgument(0);
                BrokerClusterConsensusResult committed = fixture.machine.apply(
                        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                                .beginCommandFrom(operation));
                committedBegin.complete(committed);
                return delayedBegin;
            }).when(fixture.adapter).beginOperation(any());
            java.util.concurrent.CompletionStage<?> result = fixture.service.demoteVoter(
                    new DemoteVoterCommand("delayed-begin", "b", 0L, "b:18120"));
            BrokerClusterConsensusResult committed = committedBegin.get(5, java.util.concurrent.TimeUnit.SECONDS);
            fixture.executor.onRoleChanged(0L, 2L);
            fixture.executor.onRoleChanged(7L, 3L);
            delayedBegin.complete(committed);
            result.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fixture.targets).isEmpty();
            assertThat(fixture.machine.findOperation(new ClusterOperationId("delayed-begin"))
                    .orElseThrow().currentMembershipStep().currentPermit()).isNull();
            assertThat(fixture.commits).hasValue(0);
        }
    }

    @ParameterizedTest
    @EnumSource(NativeOutcome.class)
    void fixedSecondTargetAndBusinessCommitWaitForExactFirstPermitDrain(NativeOutcome outcome) throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            java.util.concurrent.CompletionStage<?> result = fixture.service.demoteVoter(
                    new DemoteVoterCommand("native-demote", "b", 0L, "b:18120"));
            assertThat(fixture.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            BrokerClusterOperation pending = fixture.machine.findOperation(new ClusterOperationId("native-demote")).orElseThrow();
            assertThat(pending.currentMembershipStep().currentPermit().drained()).isFalse();
            assertThat(fixture.targets).containsExactly(pending.membershipSteps().getFirst().target());
            assertThat(fixture.commits.get()).isZero();
            fixture.service.resume(pending.id());
            assertThat(fixture.targets).hasSize(1);
            if (outcome == NativeOutcome.ROLE_RESTART) {
                fixture.executor.onRoleChanged(0L, 2L);
                fixture.executor.onRoleChanged(7L, 3L);
            }
            fixture.first.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                    outcome == NativeOutcome.UNKNOWN
                            ? com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.UNAVAILABLE
                            : com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED,
                    0, null, null));
            if (outcome == NativeOutcome.APPLIED) {
                result.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                BrokerClusterOperation completed = fixture.machine.findOperation(pending.id()).orElseThrow();
                assertThat(fixture.targets).containsExactlyElementsOf(completed.membershipSteps().stream()
                        .map(com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipStep::target).toList());
                assertThat(completed.membershipReadyToCommit()).isTrue();
                assertThat(completed.phase()).isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.SUCCEEDED);
                assertThat(fixture.commits.get()).isEqualTo(1);
                assertThat(fixture.machine.getCluster().findNode(new BrokerId("b")).orElseThrow().membershipRole())
                        .isEqualTo(BrokerMembershipRole.LEARNER);
            } else {
                // worker 上的栅栏在结果事实之后消费，避免用 sleep 猜测迟到 callback。
                java.util.concurrent.CountDownLatch barrier = new java.util.concurrent.CountDownLatch(1);
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                while (true) {
                    try { fixture.worker.execute(barrier::countDown); break; }
                    catch (java.util.concurrent.RejectedExecutionException saturated) {
                        assertThat(System.nanoTime()).isLessThan(deadline);
                        Thread.yield();
                    }
                }
                assertThat(barrier.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(fixture.targets).hasSize(1);
                assertThat(fixture.commits.get()).isZero();
                assertThat(fixture.machine.findOperation(pending.id()).orElseThrow().phase())
                        .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.PENDING);
                if (outcome == NativeOutcome.UNKNOWN) {
                    assertThat(result.toCompletableFuture()).isNotDone();
                    assertThat(fixture.machine.findOperation(pending.id()).orElseThrow()
                            .currentMembershipStep().currentPermit().drained()).isFalse();
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void learnerPublicationReleasesAttemptBeforeReplay() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<Long> progress = new CompletableFuture<>();
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(call -> {
                return progress;
            });
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> first =
                    fixture.service.confirmLearner(fixture.command());
            progress.complete(10L);
            assertThat(first.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
            assertThat(fixture.terminalSubmits).hasValue(1);
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.DUPLICATE);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void confirmationAndRecoveryReportInProgressInEitherOrder(boolean recoveryFirst) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture()) {
            fixture.blockWorker();
            java.util.concurrent.CompletionStage<?> first = recoveryFirst
                    ? fixture.service.resume(fixture.operation.id())
                    : fixture.service.confirmLearner(fixture.command());
            java.util.concurrent.CompletionStage<?> second = recoveryFirst
                    ? fixture.service.confirmLearner(fixture.command())
                    : fixture.service.resume(fixture.operation.id());
            Object inProgress = second.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(((com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult) inProgress).status().name())
                    .isEqualTo("IN_PROGRESS");
            assertThat(first.toCompletableFuture().isDone()).isFalse();
            fixture.release.countDown();
            first.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fixture.terminalSubmits).hasValue(recoveryFirst ? 1 : 0);
            if (!recoveryFirst) {
                assertThat(fixture.machine.findOperation(fixture.operation.id()).orElseThrow().phase())
                        .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.RAFT_COMMITTED);
                fixture.service.resume(fixture.operation.id()).toCompletableFuture()
                        .get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(fixture.terminalSubmits).hasValue(1);
            }
            assertThat(fixture.beginSubmits).hasValue(0);
            assertThat(fixture.commitSubmits).hasValue(0);
        }
    }

    @org.junit.jupiter.api.Test
    void recoveryBeginObservingSucceededOperationDoesNotSubmitAgain() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer mapper =
                    com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE;
            assertThat(fixture.machine.apply(mapper.beginCommandFrom(fixture.operation)).accepted()).isTrue();
            CompletableFuture<BrokerClusterConsensusResult> delayedBegin = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
            doAnswer(ignored -> {
                requested.countDown();
                return delayedBegin;
            }).when(fixture.adapter).beginOperation(any());
            java.util.concurrent.CompletionStage<?> recovery = fixture.service.resume(fixture.operation.id());
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.machine.apply(mapper.commitCommandFrom(fixture.operation, Instant.now())).accepted()).isTrue();
            BrokerClusterOperation committed = fixture.machine.findOperation(fixture.operation.id()).orElseThrow();
            assertThat(fixture.machine.apply(mapper.membershipCompletionCommandFrom(committed, Instant.now())).accepted()).isTrue();
            delayedBegin.complete(new BrokerClusterConsensusResult(BrokerClusterConsensusResult.Status.DUPLICATE,
                    fixture.machine.findOperation(fixture.operation.id()).orElseThrow(), null, "already completed"));
            assertThat(((com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult)
                    recovery.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS)).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.DUPLICATE);
            assertThat(fixture.commitSubmits).hasValue(0);
            assertThat(fixture.terminalSubmits).hasValue(0);
        }
    }

    @org.junit.jupiter.api.Test
    void newLearnerConfirmationDoesNotTakeOverDuplicateBegin() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            doAnswer(invocation -> {
                BrokerClusterConsensusResult registered = fixture.machine.apply(
                        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                                .beginCommandFrom(invocation.getArgument(0)));
                return CompletableFuture.completedFuture(new BrokerClusterConsensusResult(
                        BrokerClusterConsensusResult.Status.DUPLICATE, registered.operation(), null, "concurrent registration"));
            }).when(fixture.adapter).beginOperation(any());
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.DUPLICATE);
            assertThat(fixture.commitSubmits).hasValue(0);
            assertThat(fixture.terminalSubmits).hasValue(0);
            fixture.service.resume(fixture.operation.id()).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fixture.commitSubmits).hasValue(1);
            assertThat(fixture.terminalSubmits).hasValue(1);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void historicalLearnerConfirmationOnlyReportsRegistration(boolean committed) throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(committed)) {
            if (!committed) {
                assertThat(fixture.machine.apply(
                        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                                .beginCommandFrom(fixture.operation)).accepted()).isTrue();
            }
            org.mockito.Mockito.clearInvocations(fixture.adapter, fixture.peer);
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.DUPLICATE);
            assertThat(fixture.beginSubmits).hasValue(0);
            assertThat(fixture.commitSubmits).hasValue(0);
            assertThat(fixture.terminalSubmits).hasValue(0);
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).confirmLeadership(any());
            org.mockito.Mockito.verify(fixture.peer, org.mockito.Mockito.never()).fetchQuorumReadIndex(any());
            fixture.service.resume(fixture.operation.id()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fixture.machine.findOperation(fixture.operation.id()).orElseThrow().phase())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.SUCCEEDED);
            assertThat(fixture.commitSubmits).hasValue(committed ? 0 : 1);
            assertThat(fixture.terminalSubmits).hasValue(1);
        }
    }

    @org.junit.jupiter.api.Test
    void invalidLearnerEpochFailsBeforeInflightRegistration() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand invalid =
                    new com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand("sync", "b", -1L, "b:18120");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.confirmLearner(invalid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("routing epoch must not be negative");
            assertThat(fixture.beginSubmits).hasValue(0);
            assertThat(fixture.commitSubmits).hasValue(0);
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
        }
    }

    @org.junit.jupiter.api.Test
    void terminalReplayPublicationIsAtomicWithFirstCompletion() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<Long> progress = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(call -> {
                requested.countDown(); return progress;
            });
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> first =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> shared =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(shared.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status().name())
                    .isEqualTo("IN_PROGRESS");
            CompletableFuture<Void> observed = new CompletableFuture<>();
            first.whenComplete((value, failure) -> {
                try {
                    fixture.service.confirmLearner(fixture.command()).whenComplete((replay, replayFailure) -> {
                        if (replayFailure != null) {
                            observed.completeExceptionally(replayFailure);
                        } else {
                            try {
                                assertThat(replay.status()).isEqualTo(BrokerClusterConsensusResult.Status.DUPLICATE);
                                observed.complete(null);
                            } catch (Throwable assertion) { observed.completeExceptionally(assertion); }
                        }
                    });
                } catch (Throwable assertion) { observed.completeExceptionally(assertion); }
            });
            assertThat(first.toCompletableFuture().cancel(false)).isTrue();
            progress.complete(10L);
            observed.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fixture.terminalSubmits.get()).isEqualTo(1);
        }
    }

    @org.junit.jupiter.api.Test
    void nonTerminalLearnerOutcomesRetainExplicitRecoveryBudget() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            when(fixture.peer.fetchQuorumReadIndex(any())).thenReturn(CompletableFuture.failedFuture(
                    new IllegalStateException("unknown progress")));
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.UNAVAILABLE);
            assertThat(fixture.machine.findOperation(new ClusterOperationId("sync"))).isEmpty();
            when(fixture.peer.fetchQuorumReadIndex(any())).thenReturn(CompletableFuture.completedFuture(10L));
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
            assertThat(fixture.beginSubmits.get()).isEqualTo(1);
            assertThat(fixture.commitSubmits.get()).isEqualTo(1);
        }
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            when(fixture.peer.fetchQuorumReadIndex(any())).thenReturn(CompletableFuture.completedFuture(0L));
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> rejected =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(rejected.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.REJECTED);
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> replay =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(replay.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.REJECTED);
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.REJECTED);
            assertThat(fixture.beginSubmits.get()).isZero();
        }
    }

    @org.junit.jupiter.api.Test
    void terminalLearnerReplayUsesCommittedIdentityWithoutNewBegin() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.APPLIED);
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> replay =
                    fixture.service.confirmLearner(fixture.command());
            assertThat(replay.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.DUPLICATE);
            assertThat(replay.toCompletableFuture().join().acceptedEpoch()).isZero();
            assertThat(replay.toCompletableFuture().join().targetEpoch()).isZero();
            for (int i = 0; i < 3; i++) {
                assertThat(fixture.service.confirmLearner(fixture.command()).toCompletableFuture()
                        .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                        .isEqualTo(BrokerClusterConsensusResult.Status.DUPLICATE);
            }
            assertThat(fixture.beginSubmits.get()).isEqualTo(1);
            assertThat(fixture.commitSubmits.get()).isEqualTo(1);
            assertThat(fixture.terminalSubmits.get()).isEqualTo(1);
        }
    }

    @org.junit.jupiter.api.Test
    void learnerIdentityConflictRejectsInflightImmediatelyAndChecksCommittedIdentityAfterCompletion() throws Exception {
        try (CommittedLearnerFixture fixture = new CommittedLearnerFixture(false)) {
            CompletableFuture<Long> progress = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
            when(fixture.peer.fetchQuorumReadIndex(any())).thenAnswer(call -> {
                requested.countDown(); return progress;
            });
            java.util.concurrent.CompletionStage<?> first = fixture.service.confirmLearner(fixture.command());
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand conflict =
                    new com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand("sync", "other", 42L, "other:18120");
            java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult> rejected =
                    fixture.service.confirmLearner(conflict);
            assertThat(rejected.toCompletableFuture()).isCompleted();
            progress.complete(10L);
            first.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fixture.service.confirmLearner(conflict).toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.REJECTED);
            assertThat(rejected.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(BrokerClusterConsensusResult.Status.REJECTED);
            assertThat(rejected.toCompletableFuture().join().acceptedEpoch()).isEqualTo(42L);
            assertThat(rejected.toCompletableFuture().join().targetEpoch()).isNull();
            assertThat(fixture.beginSubmits.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"DIGEST", "TYPE", "TARGET", "FAILED"})
    void nativeReplayUsesPersistedIdentityAndNeverTreatsFailureAsSuccess(String mismatch) throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            BrokerCluster cluster = fixture.machine.getCluster();
            DemoteVoterCommand command = new DemoteVoterCommand("replay", "b", 0L, "b:18120");
            BrokerClusterOperation stored = cluster.demoteVoter(VoterDemotionIntent.builder()
                    .operationId(new ClusterOperationId("replay")).commandDigest(command.digest())
                    .expectedRevision(cluster.revision()).brokerId(new BrokerId("b"))
                    .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.EPOCH).build()).operation();
            com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer mapper =
                    com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE;
            assertThat(fixture.machine.apply(mapper.beginCommandFrom(stored)).accepted()).isTrue();
            if (mismatch.equals("FAILED")) {
                stored = BrokerClusterOperation.builder().id(stored.id()).commandDigest(stored.commandDigest())
                        .type(stored.type()).targetBrokerId(stored.targetBrokerId())
                        .acceptedEpoch(stored.acceptedEpoch()).acceptedRevision(stored.acceptedRevision())
                        .resultEpoch(stored.resultEpoch())
                        .phase(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.FAILED)
                        .createdAt(stored.createdAt()).completedAt(Instant.EPOCH)
                        .minimumVoterCount(stored.minimumVoterCount()).membershipSteps(stored.membershipSteps())
                        .error(new com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason("failed restored operation"))
                        .build();
                com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot snapshot = fixture.machine.snapshotState();
                fixture.machine.restore(new com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot(
                        snapshot.formatVersion(), snapshot.clusterId(), snapshot.state(), List.of(stored),
                        snapshot.fencingOperation(), snapshot.membershipEndpoints()));
            }
            BrokerClusterOperation requested = BrokerClusterOperation.begin(
                    com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal.builder()
                            .operationId(stored.id())
                            .commandDigest(mismatch.equals("DIGEST")
                                    ? new com.co.kc.imchat.broker.domain.cluster.model.CommandDigest("different")
                                    : stored.commandDigest())
                            .expectedRevision(stored.acceptedRevision())
                            .operationType(mismatch.equals("TYPE")
                                    ? com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType.REMOVE_BROKER
                                    : stored.type())
                            .targetBrokerId(mismatch.equals("TARGET") ? new BrokerId("a") : stored.targetBrokerId())
                            .minimumVoterCount(stored.minimumVoterCount()).changedAt(Instant.EPOCH).build(),
                    stored.acceptedRevision(), stored.acceptedEpoch());
            BrokerCluster requestSnapshot = spy(fixture.machine.getCluster().snapshot());
            when(requestSnapshot.findOperation(requested.id())).thenReturn(java.util.Optional.of(requested));
            when(fixture.adapter.getClusterSnapshot()).thenAnswer(call -> {
                if (Thread.currentThread().getName().equals("t5-native-worker")) {
                    return fixture.machine.getCluster();
                }
                return requestSnapshot;
            });
            BrokerClusterConsensusResult result = (BrokerClusterConsensusResult) fixture.service.resume(requested.id())
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.status()).isEqualTo(BrokerClusterConsensusResult.Status.REJECTED);
            assertThat(result.operation().id()).isEqualTo(stored.id());
            assertThat(result.operation().acceptedEpoch()).isEqualTo(stored.acceptedEpoch());
            assertThat(result.operation().resultEpoch()).isEqualTo(stored.resultEpoch());
            assertThat(fixture.targets).isEmpty();
            assertThat(fixture.commits.get()).isZero();
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).beginOperation(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).registerMembershipPermit(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).completeMembershipOperation(any());
        }
    }

    @org.junit.jupiter.api.Test
    void explicitDrainedRetransmissionSharesAutomaticAndExplicitInflightWithoutGrowth() throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            CompletableFuture<com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain> proof =
                    new CompletableFuture<>();
            java.util.concurrent.CountDownLatch reading = new java.util.concurrent.CountDownLatch(1);
            when(fixture.adapter.confirmMembershipPermitDrain(any())).thenAnswer(call -> {
                reading.countDown(); return proof;
            });
            fixture.service.demoteVoter(new DemoteVoterCommand("native-demote", "b", 0L, "b:18120"));
            assertThat(fixture.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            fixture.first.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                    com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED, 0, null, null));
            assertThat(reading.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            ClusterOperationId id = new ClusterOperationId("native-demote");
            java.util.concurrent.CompletionStage<?> view = fixture.service.sendExecutionResult(id, new BrokerId("a"));
            for (int index = 0; index < 5000; index++) {
                assertThat(view == fixture.service.sendExecutionResult(id, new BrokerId("a"))).isTrue();
            }
            assertThat(fixture.service.sendExecutionResult(id, new BrokerId("b"))
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.UNKNOWN);
            view.toCompletableFuture().cancel(false);
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.times(1)).confirmMembershipPermitDrain(any());
            proof.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(
                    new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L), 1L,
                    new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L)));
            view.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(fixture.machine.findOperation(id).orElseThrow().phase())
                            .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.SUCCEEDED));
            java.lang.reflect.Field slotsField = BrokerMembershipExecutor.class.getDeclaredField("slots");
            slotsField.setAccessible(true);
            Map<?, ?> slots = (Map<?, ?>) slotsField.get(fixture.executor);
            int size = slots.size();
            for (int round = 0; round < 100; round++) {
                assertThat(fixture.service.sendExecutionResult(id, new BrokerId("a"))
                        .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                        .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.DUPLICATE);
                assertThat(slots).hasSize(size);
                for (Object slot : slots.values()) {
                    java.lang.reflect.Field pending = slot.getClass().getDeclaredField("confirmationTask");
                    pending.setAccessible(true);
                    assertThat(pending.get(slot)).isNull();
                    java.lang.reflect.Field completion = slot.getClass().getDeclaredField("sendCompletion");
                    completion.setAccessible(true);
                    CompletableFuture<?> current = (CompletableFuture<?>) completion.get(slot);
                    if (current != null) { assertThat(current.getNumberOfDependents()).isZero(); }
                    assertThat(java.util.Arrays.stream(slot.getClass().getDeclaredFields())
                            .noneMatch(field -> java.util.Collection.class.isAssignableFrom(field.getType()))).isTrue();
                }
            }
            assertThat(fixture.targets).hasSize(2);
        }
    }

    @org.junit.jupiter.api.Test
    void explicitDrainedRetransmissionRejectsUnknownForeignAndUntrustedReceiver() throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            fixture.service.demoteVoter(new DemoteVoterCommand("native-demote", "b", 0L, "b:18120"));
            assertThat(fixture.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            fixture.first.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                    com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.UNAVAILABLE, 0, null, null));
            assertThat(fixture.service.sendExecutionResult(new ClusterOperationId("native-demote"), new BrokerId("a"))
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.UNKNOWN);
            assertThat(fixture.executor.undrainedPermitCount()).isEqualTo(1);
            assertThat(fixture.targets).hasSize(1);
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).confirmMembershipPermitDrain(any());
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void explicitDrainedRetransmissionRevalidatesLifecycleBeforeSendAndPublication(boolean afterSend, boolean fault)
            throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            fixture.prepareLostLeaderDrain();
            java.util.concurrent.CountDownLatch sent = new java.util.concurrent.CountDownLatch(1);
            CompletableFuture<com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain> response =
                    new CompletableFuture<>();
            when(fixture.adapter.confirmMembershipPermitDrain(any())).thenAnswer(call -> {
                sent.countDown(); return response;
            });
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch blocked = new java.util.concurrent.CountDownLatch(1);
            if (!afterSend) {
                fixture.worker.execute(() -> {
                    blocked.countDown();
                    try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                });
                assertThat(blocked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
            when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(true);
            java.util.concurrent.CompletionStage<?> result = fixture.service.sendExecutionResult(
                    new ClusterOperationId("native-demote"), new BrokerId("a"));
            when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(true);
            if (afterSend) { assertThat(sent.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
            if (fault) { fixture.executor.faultLatch().fail(new IllegalStateException("controlled send fault")); }
            else { fixture.executor.close(); }
            response.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(
                    new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L), 1L,
                    new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L)));
            release.countDown();
            fixture.worker.shutdown();
            assertThat(fixture.worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            if (result.toCompletableFuture().isDone()) {
                assertThat(((com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult)
                        result.toCompletableFuture().get()).status())
                        .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.UNKNOWN);
            }
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.times(afterSend ? 1 : 0))
                    .confirmMembershipPermitDrain(any());
            assertThat(fixture.executor.undrainedPermitCount()).isZero();
            assertThat(fixture.targets).hasSize(1);
            assertThat(fixture.machine.findOperation(new ClusterOperationId("native-demote")).orElseThrow()
                    .currentMembershipStep().currentPermit().drained()).isFalse();
        }
    }

    @org.junit.jupiter.api.Test
    void explicitDrainedRetransmissionUsesExactLocalFactWithoutNativeOrLeaderHint() throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            fixture.prepareLostLeaderDrain();
            when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(true);
            assertThat(fixture.service.sendExecutionResult(new ClusterOperationId("native-demote"), new BrokerId("a"))
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED);
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.times(1)).confirmMembershipPermitDrain(any());
            assertThat(fixture.service.sendExecutionResult(new ClusterOperationId("native-demote"), new BrokerId("b"))
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.UNKNOWN);
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.times(1)).confirmMembershipPermitDrain(any());
            assertThat(fixture.targets).hasSize(1);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void explicitDrainedRetransmissionSelectsPendingStepBeforeHistoricalSlots(boolean reverse) throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            fixture.second = new CompletableFuture<>();
            fixture.service.demoteVoter(new DemoteVoterCommand("native-demote", "b", 0L, "b:18120"));
            assertThat(fixture.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            fixture.first.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                    com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED, 0, null, null));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(fixture.targets).hasSize(2));
            ClusterOperationId id = new ClusterOperationId("native-demote");
            BrokerClusterOperation pending = fixture.machine.findOperation(id).orElseThrow();
            assertThat(pending.membershipSteps().getFirst().successful()).isTrue();
            assertThat(pending.membershipSteps().getLast().hasUndrainedPermit()).isTrue();
            java.lang.reflect.Field slotsField = BrokerMembershipExecutor.class.getDeclaredField("slots");
            slotsField.setAccessible(true);
            Map<?, ?> slots = (Map<?, ?>) slotsField.get(fixture.executor);
            // 暂停在第二个真实native异步结果，重排账本不产生任何执行能力。
            java.lang.reflect.Field mutexField = BrokerMembershipExecutor.class.getDeclaredField("mutex");
            mutexField.setAccessible(true);
            synchronized (mutexField.get(fixture.executor)) {
                List<Map.Entry<?, ?>> entries = new java.util.ArrayList<>(slots.entrySet());
                if (reverse) { java.util.Collections.reverse(entries); }
                Map<Object, Object> ordered = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : entries) { ordered.put(entry.getKey(), entry.getValue()); }
                slotsField.set(fixture.executor, ordered);
            }
            org.mockito.Mockito.clearInvocations(fixture.adapter);
            assertThat(fixture.service.sendExecutionResult(id, new BrokerId("a"))
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.UNKNOWN);
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).confirmMembershipPermitDrain(any());
            when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(false);
            fixture.executor.onRoleChanged(0L, 2L);
            fixture.second.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                    com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED, 0, null, null));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(fixture.executor.undrainedPermitCount()).isZero());
            when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(true);
            assertThat(fixture.service.sendExecutionResult(id, new BrokerId("a"))
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED);
            org.mockito.ArgumentCaptor<com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand> captor =
                    org.mockito.ArgumentCaptor.forClass(com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand.class);
            org.mockito.Mockito.verify(fixture.adapter).confirmMembershipPermitDrained(captor.capture());
            assertThat(captor.getValue().stepId()).isEqualTo(pending.membershipSteps().getLast().stepId());
            org.mockito.Mockito.clearInvocations(fixture.adapter);
            assertThat(fixture.service.sendExecutionResult(id, new BrokerId("a"))
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.DUPLICATE);
            org.mockito.Mockito.verify(fixture.adapter).confirmMembershipPermitDrained(captor.capture());
            assertThat(captor.getValue().stepId()).isEqualTo(pending.membershipSteps().getLast().stepId());
            assertThat(fixture.targets).hasSize(2);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"FOREIGN_INCARNATION", "REMOTE_FACT", "MISSING_SLOT", "MISSING_BINDING", "NON_TARGET", "MISSING_PERMIT"})
    void explicitDrainedRetransmissionRejectsUnknownForeignAndUntrustedReceiver(String invalid) throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            fixture.prepareLostLeaderDrain();
            java.lang.reflect.Field slotsField = BrokerMembershipExecutor.class.getDeclaredField("slots");
            slotsField.setAccessible(true);
            Map<?, ?> slots = (Map<?, ?>) slotsField.get(fixture.executor);
            Object slot = slots.values().iterator().next();
            if (invalid.equals("MISSING_SLOT")) { slots.clear(); }
            if (invalid.equals("REMOTE_FACT")) {
                java.lang.reflect.Field reserved = slot.getClass().getDeclaredField("nativeReserved");
                reserved.setAccessible(true); reserved.set(slot, false);
            }
            if (invalid.equals("FOREIGN_INCARNATION")) {
                java.lang.reflect.Field identity = slot.getClass().getDeclaredField("identity");
                identity.setAccessible(true);
                com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit original =
                        (com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit) identity.get(slot);
                identity.set(slot, new com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit(
                        original.permitId(), original.operationId(), original.stepId(), original.target(),
                        original.executorBrokerId(), "other-incarnation", original.result()));
            }
            if (invalid.equals("MISSING_BINDING") || invalid.equals("MISSING_PERMIT")) {
                BrokerCluster snapshot = spy(fixture.machine.getCluster());
                if (invalid.equals("MISSING_BINDING")) { when(snapshot.isMembershipTargetVoter(any(), any())).thenReturn(false); }
                else { when(snapshot.findOperation(any())).thenReturn(java.util.Optional.empty()); }
                when(fixture.adapter.getClusterSnapshot()).thenReturn(snapshot);
            }
            BrokerId receiver = new BrokerId(invalid.equals("NON_TARGET") ? "b" : "a");
            assertThat(fixture.service.sendExecutionResult(new ClusterOperationId("native-demote"), receiver)
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.UNKNOWN);
            org.mockito.Mockito.verify(fixture.peer, org.mockito.Mockito.never()).confirmMembershipPermitDrained(any(), any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).confirmMembershipPermitDrain(any());
            assertThat(fixture.targets).hasSize(1);
        }
    }

    @ParameterizedTest
    @EnumSource(Stop.class)
    void periodicDiagnosticPendingFactCannotReadNodeAfterLifecycleInvalidation(Stop stop) throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            fixture.worker.execute(() -> {
                entered.countDown();
                try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            });
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 5000; index++) { fixture.service.diagnoseMembershipConfiguration(); }
            switch (stop) {
                case SAME_TERM_RESTART -> { fixture.executor.onRoleChanged(0L, 2L); fixture.executor.onRoleChanged(7L, 3L); }
                case FAULT -> fixture.executor.faultLatch().fail(new IllegalStateException("diagnostic fault"));
                case CLOSE -> fixture.executor.close();
            }
            release.countDown();
            fixture.worker.shutdown();
            assertThat(fixture.worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).readLeaderMembershipConfiguration();
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).changeMembershipConfiguration(any());
            java.lang.reflect.Field slotsField = BrokerMembershipExecutor.class.getDeclaredField("slots");
            slotsField.setAccessible(true);
            assertThat((Map<?, ?>) slotsField.get(fixture.executor)).isEmpty();
        }
    }

    @org.junit.jupiter.api.Test
    void periodicDiagnosticUsesWorkerAndDoesNotRepairDriftOrChangeObservers() throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            java.util.concurrent.CountDownLatch read = new java.util.concurrent.CountDownLatch(1);
            when(fixture.adapter.readLeaderMembershipConfiguration()).thenAnswer(call -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-native-worker");
                read.countDown();
                return new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                        java.util.Set.of(new MembershipEndpoint("a:18120")), java.util.Set.of());
            });
            fixture.service.diagnoseMembershipConfiguration();
            assertThat(read.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(fixture.diagnosticRegistry.get("im.broker.membership.configuration.drift").gauge().value()).isEqualTo(1));
            assertThat(fixture.machine.getCluster().findObservers()).containsExactlyInAnyOrder(new BrokerId("a"), new BrokerId("b"));
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).beginOperation(any());
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.never()).changeMembershipConfiguration(any());
        }
    }

    @org.junit.jupiter.api.Test
    void explicitDrainedRetransmissionNewReservationCannotCreateSecondSendOwner() throws Exception {
        try (NativeFixture fixture = new NativeFixture()) {
            fixture.prepareLostLeaderDrain();
            CompletableFuture<com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain> proof = new CompletableFuture<>();
            java.util.concurrent.CountDownLatch reading = new java.util.concurrent.CountDownLatch(1);
            when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(true);
            when(fixture.adapter.confirmMembershipPermitDrain(any())).thenAnswer(call -> {
                reading.countDown(); return proof;
            });
            ClusterOperationId id = new ClusterOperationId("native-demote");
            java.util.concurrent.CompletionStage<?> first = fixture.service.sendExecutionResult(id, new BrokerId("a"));
            assertThat(reading.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            // Reserve先于Register；预留后登记失败也永久保留，不能改变已有operation发送占用。
            fixture.executor.onRoleChanged(7L, 3L);
            fixture.executor.reserve(id, "final-target", "!later-reservation", fixture.machine.findOperation(id)
                    .orElseThrow().membershipSteps().getLast().target());
            java.util.concurrent.CompletionStage<?> repeated = fixture.service.sendExecutionResult(id, new BrokerId("a"));
            assertThat(repeated == first).as("a later reservation cannot replace the operation send owner").isTrue();
            org.mockito.Mockito.verify(fixture.adapter, org.mockito.Mockito.times(1)).confirmMembershipPermitDrain(any());
            proof.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(
                    new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L), 3L,
                    new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L)));
            first.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fixture.targets).hasSize(1);
        }
    }

    private static final class NativeFixture implements AutoCloseable {
        private final io.micrometer.core.instrument.simple.SimpleMeterRegistry diagnosticRegistry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        private final BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        private final BrokerPeerAdapter peer = mock(BrokerPeerAdapter.class);
        private final com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine machine;
        private final java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("t5-native-worker").factory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        private final BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker);
        private final java.util.concurrent.CompletableFuture<com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult> first = new CompletableFuture<>();
        private final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.List<com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership> targets =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicInteger commits = new AtomicInteger();
        private CompletableFuture<com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult> second;
        private final BrokerMembershipAppService service;

        private NativeFixture() {
            machine = new com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine("cluster-a",
                    new com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState(0L, 0L,
                            com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                            List.of(new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("a", "a:18020",
                                            com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                                            com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.ACTIVE),
                                    new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("b", "b:18020",
                                            com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                                            com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.INACTIVE))),
                    100, Map.of("a", "a:18120", "b", "b:18120"));
            com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer mapper =
                    com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE;
            BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
            when(adapter.getLocalBroker()).thenReturn(local);
            when(local.getId()).thenReturn(new BrokerId("a"));
            when(local.isLeader()).thenReturn(true);
            when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L)));
            when(adapter.getClusterId()).thenReturn(new com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId("cluster-a"));
            when(adapter.getClusterSnapshot()).thenAnswer(call -> machine.getCluster());
            when(adapter.findOperation(any())).thenAnswer(call -> machine.findOperation(call.getArgument(0)));
            when(adapter.readLeaderMembershipConfiguration()).thenReturn(new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                    java.util.Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120")), java.util.Set.of()));
            when(adapter.beginOperation(any())).thenAnswer(call -> CompletableFuture.completedFuture(machine.apply(mapper.beginCommandFrom(call.getArgument(0)))));
            when(adapter.registerMembershipPermit(any())).thenAnswer(call -> CompletableFuture.completedFuture(machine.apply(call.getArgument(0))));
            when(adapter.confirmMembershipPermitDrained(any())).thenAnswer(call -> CompletableFuture.completedFuture(machine.apply(call.getArgument(0))));
            when(adapter.isMembershipPermitDrainCurrent(any())).thenReturn(true);
            when(adapter.confirmMembershipPermitDrain(any())).thenReturn(CompletableFuture.completedFuture(
                    new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(
                            new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L), 1L,
                            new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L))));
            when(adapter.fetchQuorumReadIndex()).thenReturn(CompletableFuture.completedFuture(10L));
            when(adapter.changeMembershipConfiguration(any())).thenAnswer(call -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-native-worker");
                targets.add(call.getArgument(0));
                if (targets.size() == 1) { entered.countDown(); return first; }
                if (second != null) { return second; }
                return CompletableFuture.completedFuture(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                        com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED, 0, null, null));
            });
            when(adapter.commitOperation(any(), any())).thenAnswer(call -> {
                BrokerClusterOperation operation = call.getArgument(0);
                assertThat(operation.membershipReadyToCommit()).isTrue();
                assertThat(call.<ClusterRevision>getArgument(1)).isEqualTo(machine.getCluster().revision());
                commits.incrementAndGet();
                return CompletableFuture.completedFuture(machine.apply(mapper.commitCommandFrom(operation, call.getArgument(1), Instant.now())));
            });
            when(adapter.completeMembershipOperation(any())).thenAnswer(call -> CompletableFuture.completedFuture(
                    machine.apply(mapper.membershipCompletionCommandFrom(call.getArgument(0), Instant.now()))));
            executor.onRoleChanged(7L, 1L);
            com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics metrics =
                    new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics();
            metrics.bindTo(diagnosticRegistry);
            MembershipOperationService membershipOperations = new MembershipOperationService(
                    new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository());
            service = new BrokerMembershipAppService(adapter, peer, new MinimumVoterCount(1), executor, metrics,
                    membershipOperations);
        }

        private void prepareLostLeaderDrain() throws Exception {
            service.demoteVoter(new DemoteVoterCommand("native-demote", "b", 0L, "b:18120"));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            when(adapter.getLocalBroker().isLeader()).thenReturn(false);
            executor.onRoleChanged(0L, 2L);
            first.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                    com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED, 0, null, null));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(executor.undrainedPermitCount()).isZero());
            java.util.concurrent.CountDownLatch barrier = new java.util.concurrent.CountDownLatch(1);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (true) {
                try { worker.execute(barrier::countDown); break; }
                catch (java.util.concurrent.RejectedExecutionException full) {
                    assertThat(System.nanoTime()).isLessThan(deadline); Thread.yield();
                }
            }
            assertThat(barrier.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        @Override
        public void close() throws Exception {
            diagnosticRegistry.close();
            executor.close();
            assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class CommittedLearnerFixture implements AutoCloseable {
        private final com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine machine;
        private final BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        private final BrokerPeerAdapter peer = mock(BrokerPeerAdapter.class);
        private final AtomicInteger beginSubmits = new AtomicInteger();
        private final AtomicInteger commitSubmits = new AtomicInteger();
        private volatile BrokerCluster snapshotOverride;
        private final BrokerClusterOperation operation;
        private final java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("t5-membership-worker").factory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        private final BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker);
        private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        private final AtomicInteger terminalSubmits = new AtomicInteger();
        private volatile String submitThread;
        private volatile boolean pauseWorkerSnapshot;
        private final java.util.concurrent.CountDownLatch snapshotEntered = new java.util.concurrent.CountDownLatch(1);
        private final BrokerMembershipAppService service;

        private CommittedLearnerFixture() { this(true); }

        private CommittedLearnerFixture(boolean committed) {
            machine = new com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine("cluster-a",
                    new com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState(0L, 0L,
                            com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                            List.of(new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("a", "a:18020",
                                            com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                                            com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.ACTIVE),
                                    new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("b", "b:18020",
                                            com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.LEARNER_SYNCING,
                                            com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.INACTIVE))),
                    100, Map.of("a", "a:18120", "b", "b:18120"));
            com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand command =
                    new com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand("sync", "b", 0L, "b:18120");
            BrokerCluster proposed = machine.getCluster();
            BrokerClusterOperation proposal = proposed.confirmLearner(
                    com.co.kc.imchat.broker.domain.cluster.model.LearnerSyncCompletionIntent.builder()
                            .operationId(new ClusterOperationId("sync")).commandDigest(command.digest())
                            .expectedRevision(proposed.revision()).brokerId(new BrokerId("b"))
                            .changedAt(Instant.EPOCH).build()).operation();
            com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer mapper =
                    com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE;
            if (committed) {
                assertThat(machine.apply(mapper.beginCommandFrom(proposal)).accepted()).isTrue();
                assertThat(machine.apply(mapper.commitCommandFrom(proposal, Instant.EPOCH)).accepted()).isTrue();
                operation = machine.findOperation(proposal.id()).orElseThrow();
                assertThat(operation.phase()).isEqualTo(
                        com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.RAFT_COMMITTED);
                assertThat(machine.getCluster().findNode(new BrokerId("b")).orElseThrow().membershipRole())
                        .isEqualTo(BrokerMembershipRole.LEARNER);
            } else { operation = proposal; }
            BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
            when(adapter.getLocalBroker()).thenReturn(local);
            when(local.isLeader()).thenReturn(true);
            when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L)));
            when(adapter.getClusterSnapshot()).thenAnswer(ignored -> {
                if (pauseWorkerSnapshot && Thread.currentThread().getName().equals("t5-membership-worker")) {
                    pauseWorkerSnapshot = false;
                    snapshotEntered.countDown();
                    assertThat(release.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                }
                return snapshotOverride == null ? machine.getCluster() : snapshotOverride.snapshot();
            });
            when(adapter.readLeaderMembershipConfiguration()).thenReturn(new com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership(
                    java.util.Set.of(new MembershipEndpoint("a:18120")), java.util.Set.of(new MembershipEndpoint("b:18120"))));
            when(adapter.confirmLeadership(any())).thenReturn(CompletableFuture.completedFuture(
                    new com.co.kc.imchat.broker.domain.authority.model.QuorumRead(
                            new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L),
                            new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L))));
            when(peer.fetchQuorumReadIndex(any())).thenReturn(CompletableFuture.completedFuture(10L));
            when(adapter.beginOperation(any())).thenAnswer(invocation -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-membership-worker");
                beginSubmits.incrementAndGet();
                return CompletableFuture.completedFuture(machine.apply(mapper.beginCommandFrom(invocation.getArgument(0))));
            });
            when(adapter.commitOperation(any())).thenAnswer(invocation -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("t5-membership-worker");
                commitSubmits.incrementAndGet();
                return CompletableFuture.completedFuture(machine.apply(mapper.commitCommandFrom(invocation.getArgument(0), Instant.now())));
            });
            when(adapter.findOperation(any())).thenAnswer(invocation -> machine.findOperation(invocation.getArgument(0)));
            when(adapter.completeMembershipOperation(any())).thenAnswer(invocation -> {
                submitThread = Thread.currentThread().getName();
                terminalSubmits.incrementAndGet();
                BrokerClusterConsensusResult result = machine.apply(mapper.membershipCompletionCommandFrom(
                        invocation.getArgument(0), Instant.now()));
                finished.countDown();
                return CompletableFuture.completedFuture(result);
            });
            executor.onRoleChanged(7L, 1L);
            MembershipOperationService membershipOperations = new MembershipOperationService(
                    new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository());
            service = new BrokerMembershipAppService(adapter, peer, new MinimumVoterCount(1), executor,
                    new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(), membershipOperations);
        }

        private com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand command() {
            return new com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand("sync", "b", 0L, "b:18120");
        }

        private void blockWorker() throws Exception {
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            worker.execute(() -> {
                entered.countDown();
                try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            });
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        private void recover(Entry entry) {
            if (entry == Entry.SCHEDULER) {
                new BrokerMembershipScheduler(adapter, service, Duration.ZERO).reconcile();
            } else { service.resume(operation.id()); }
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            executor.close();
            worker.shutdown();
            assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private BrokerCluster initialCluster() {
        return BrokerCluster.builder().revision(new ClusterRevision(0L)).epoch(new RoutingEpoch(0L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(new BrokerNode(new BrokerId("a"), new BrokerRouteEndpoint("a:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE),
                        new BrokerNode(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.READY)))
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"),
                        new BrokerId("b"), new MembershipEndpoint("b:18120"))).build();
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
