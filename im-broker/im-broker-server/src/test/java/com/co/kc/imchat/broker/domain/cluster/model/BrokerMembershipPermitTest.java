package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BrokerMembershipPermitTest {
    static final ClusterOperationId OP = new ClusterOperationId("demote");
    static final CommandDigest DIGEST = new CommandDigest("digest");

    static BrokerCluster begun() {
        BrokerCluster cluster = BrokerCluster.builder().revision(new ClusterRevision(0L)).epoch(new RoutingEpoch(0L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(new BrokerNode(new BrokerId("a"), new BrokerRouteEndpoint("a:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE),
                        new BrokerNode(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.READY)))
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"),
                        new BrokerId("b"), new MembershipEndpoint("b:18120"))).build();
        assertThat(cluster.demoteVoter(VoterDemotionIntent.builder().operationId(OP).commandDigest(DIGEST)
                .expectedRevision(cluster.revision()).brokerId(new BrokerId("b"))
                .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.EPOCH).build()).accepted()).isTrue();
        return cluster;
    }

    @Test
    void executionPrecheckIsReadOnlyAndApplicationRechecksLatestResult() {
        BrokerCluster cluster = begun();
        BrokerMembershipStep step = cluster.findOperation(OP).orElseThrow().currentMembershipStep();
        assertThat(register(cluster, step, "check", cluster.revision()).accepted()).isTrue();
        ClusterRevision before = cluster.revision();
        BrokerClusterOperation operation = cluster.findOperation(OP).orElseThrow();
        MembershipExecutionConfirmation applied = new MembershipExecutionConfirmation(OP, step.stepId(), "check",
                new BrokerId("a"), "incarnation", step.targetDigest(), BrokerMembershipPermit.Result.TARGET_APPLIED);
        assertThat(cluster.checkMembershipExecutionConfirmation(applied).status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(cluster.revision()).isEqualTo(before);
        assertThat(cluster.findOperation(OP).orElseThrow()).isSameAs(operation);
        assertThat(operation.currentMembershipStep().currentPermit().result()).isEqualTo(BrokerMembershipPermit.Result.UNKNOWN);
        MembershipExecutionConfirmation notApplied = new MembershipExecutionConfirmation(OP, step.stepId(), "check",
                new BrokerId("a"), "incarnation", step.targetDigest(), BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
        assertThat(cluster.confirmMembershipExecution(notApplied).status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(cluster.revision()).isEqualTo(before.next());
        assertThat(cluster.confirmMembershipExecution(applied).status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(cluster.checkMembershipExecutionConfirmation(notApplied).status()).isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
        assertThat(cluster.confirmMembershipExecution(notApplied).status()).isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
        assertThat(cluster.revision()).isEqualTo(before.next());
    }

    @Test
    void finalCommitCannotBypassUnexecutedPersistentSteps() {
        BrokerCluster cluster = begun();
        assertThat(cluster.commitOperation(OP, DIGEST, new ClusterRevision(0L), Instant.EPOCH).accepted()).isFalse();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(1L));
    }

    @Test
    void genericAdvanceCannotForgeNativeMembershipSuccess() {
        BrokerCluster cluster = begun();
        assertThat(cluster.advanceOperation(OP, DIGEST, BrokerClusterOperationPhase.PENDING,
                BrokerClusterOperationPhase.RAFT_COMMITTED, null, Instant.EPOCH).accepted()).isFalse();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(1L));
    }

    @Test
    void persistedTwoTargetsRequireEachSuccessfulDrainedPermitAndCurrentRevisionCommit() {
        BrokerCluster cluster = begun();
        List<BrokerMembershipStep> targets = cluster.findOperation(OP).orElseThrow().membershipSteps();
        assertThat(targets).hasSize(2);
        MembershipEndpoint a = new MembershipEndpoint("a:18120");
        MembershipEndpoint b = new MembershipEndpoint("b:18120");
        assertThat(targets.getFirst().target()).isEqualTo(new ConsensusMembership(java.util.Set.of(a), java.util.Set.of()));
        assertThat(targets.getLast().target()).isEqualTo(new ConsensusMembership(java.util.Set.of(a), java.util.Set.of(b)));
        for (int index = 0; index < targets.size(); index++) {
            BrokerMembershipStep step = targets.get(index);
            String permitId = "permit-" + index;
            ClusterRevision before = cluster.revision();
            assertThat(register(cluster, step, permitId, before).status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
            assertThat(cluster.revision()).isEqualTo(before.next());
            assertThat(register(cluster, step, permitId, before).duplicate()).isTrue();
            assertThat(cluster.revision()).isEqualTo(before.next());
            assertThat(cluster.commitOperation(OP, DIGEST, cluster.revision(), Instant.EPOCH).accepted()).isFalse();
            assertThat(confirm(cluster, step, permitId, BrokerMembershipPermit.Result.TARGET_APPLIED).accepted()).isTrue();
            assertThat(cluster.revision()).isEqualTo(before.next().next());
            assertThat(confirm(cluster, step, permitId, BrokerMembershipPermit.Result.TARGET_APPLIED).duplicate()).isTrue();
            assertThat(cluster.revision()).isEqualTo(before.next().next());
        }
        assertThat(cluster.commitOperation(OP, DIGEST, new ClusterRevision(0L), Instant.EPOCH).accepted()).isFalse();
        assertThat(cluster.commitOperation(OP, new CommandDigest("wrong"), cluster.revision(), Instant.EPOCH).accepted()).isFalse();
        assertThat(cluster.commitOperation(OP, DIGEST, cluster.revision(), Instant.EPOCH).accepted()).isTrue();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(6L));
        assertThat(cluster.findNode(new BrokerId("b")).orElseThrow().membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER);
        assertThat(cluster.commitOperation(OP, DIGEST, cluster.revision(), Instant.EPOCH).duplicate()).isTrue();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(6L));
    }

    @Test
    void staleRegistrationConflictingIdentityAndUnknownConfirmationNeverMutateRevision() {
        BrokerCluster cluster = begun();
        BrokerMembershipStep step = cluster.findOperation(OP).orElseThrow().currentMembershipStep();
        assertThat(register(cluster, step, "p1", new ClusterRevision(0L)).accepted()).isFalse();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(1L));
        assertThat(register(cluster, step, "p1", cluster.revision()).accepted()).isTrue();
        assertThat(cluster.registerMembershipPermit(OP, step.stepId(), "p1", new BrokerId("a"), "other-process",
                cluster.revision()).accepted()).isFalse();
        assertThat(register(cluster, step, "p2", cluster.revision()).accepted()).isFalse();
        assertThat(confirm(cluster, step, "p1", BrokerMembershipPermit.Result.UNKNOWN).accepted()).isFalse();
        assertThat(cluster.confirmMembershipExecution(new MembershipExecutionConfirmation(OP, step.stepId(), "p1", new BrokerId("a"), "incarnation",
                "forged-digest", BrokerMembershipPermit.Result.TARGET_APPLIED)).accepted()).isFalse();
        assertThat(cluster.confirmMembershipExecution(new MembershipExecutionConfirmation(OP, step.stepId(), "p1", new BrokerId("a"), "other-process",
                step.targetDigest(), BrokerMembershipPermit.Result.TARGET_APPLIED)).accepted()).isFalse();
        assertThat(cluster.advanceOperation(OP, DIGEST, BrokerClusterOperationPhase.PENDING,
                BrokerClusterOperationPhase.FAILED, new OperationFailureReason("failed"), Instant.EPOCH).accepted()).isFalse();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(2L));
    }

    @Test
    void failedDrainedRetryUsesFreshIdAndOldConfirmationCannotReleaseNewPermit() {
        BrokerCluster cluster = begun();
        BrokerMembershipStep step = cluster.findOperation(OP).orElseThrow().currentMembershipStep();
        assertThat(register(cluster, step, "old", cluster.revision()).accepted()).isTrue();
        assertThat(confirm(cluster, step, "old", BrokerMembershipPermit.Result.TARGET_NOT_APPLIED).accepted()).isTrue();
        assertThat(confirm(cluster, step, "old", BrokerMembershipPermit.Result.TARGET_APPLIED).accepted()).isFalse();
        assertThat(cluster.commitOperation(OP, DIGEST, cluster.revision(), Instant.EPOCH).accepted()).isFalse();
        assertThat(register(cluster, step, "new", cluster.revision()).accepted()).isTrue();
        ClusterRevision current = cluster.revision();
        assertThat(confirm(cluster, step, "old", BrokerMembershipPermit.Result.TARGET_NOT_APPLIED).accepted()).isFalse();
        assertThat(register(cluster, step, "old", current).duplicate()).isTrue();
        assertThat(cluster.findOperation(OP).orElseThrow().currentMembershipStep().currentPermit().permitId()).isEqualTo("new");
        assertThat(cluster.revision()).isEqualTo(current);
        assertThat(confirm(cluster, step, "new", BrokerMembershipPermit.Result.TARGET_APPLIED).accepted()).isTrue();
        assertThat(register(cluster, cluster.findOperation(OP).orElseThrow().currentMembershipStep(), "old",
                cluster.revision()).accepted()).isFalse();
    }

    @Test
    void replayAndSnapshotPreserveUnknownPermitWithoutGrantingNewIncarnationAuthority() {
        BrokerCluster cluster = begun();
        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer stateTransformer =
                com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer.INSTANCE;
        com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot pending =
                new com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot(3, "cluster",
                        stateTransformer.brokerClusterStateFrom(cluster), cluster.operations(), null,
                        Map.of("a", "a:18120", "b", "b:18120"));
        com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine machine =
                new com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine("cluster",
                        pending.state(), 100, pending.membershipEndpoints());
        machine.restore(pending);
        BrokerMembershipStep step = cluster.findOperation(OP).orElseThrow().currentMembershipStep();
        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer transformer =
                com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE;
        com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand register = transformer.registerPermitCommandFrom(
                cluster.findOperation(OP).orElseThrow(), "persisted", new BrokerId("a"), "old-process", cluster.revision());
        com.co.kc.imchat.broker.support.raft.codec.BrokerClusterCommandCodec codec =
                com.co.kc.imchat.broker.support.raft.codec.BrokerClusterCommandCodec.INSTANCE;
        assertThat(machine.apply(codec.decode(codec.encode(register))).accepted()).isTrue();
        byte[] snapshot = machine.snapshot();
        machine.restore(snapshot);
        assertThat(machine.snapshot()).isEqualTo(snapshot);
        BrokerCluster recovered = machine.getCluster();
        assertThat(recovered.registerMembershipPermit(OP, step.stepId(), "replacement", new BrokerId("a"), "new-process",
                recovered.revision()).accepted()).isFalse();
        BrokerMembershipPermit permit = recovered.findOperation(OP).orElseThrow().currentMembershipStep().currentPermit();
        assertThat(permit.executorIncarnation()).isEqualTo("old-process");
        assertThat(permit.drained()).isFalse();
        assertThat(machine.apply(codec.decode(codec.encode(transformer.confirmPermitCommandFrom(step, permit,
                BrokerMembershipPermit.Result.TARGET_APPLIED)))).accepted()).isTrue();
        machine.restore(machine.snapshot());
        assertThat(machine.getCluster().revision()).isEqualTo(new ClusterRevision(3L));
        assertThat(machine.getCluster().findOperation(OP).orElseThrow().currentMembershipStep().stepId()).isEqualTo("final-target");
        assertThat(transformer.commitCommandFrom(machine.getCluster().findOperation(OP).orElseThrow(),
                machine.getCluster().revision(), Instant.EPOCH).expectedRevision()).isEqualTo(new ClusterRevision(3L));
    }

    @Test
    void successfulTerminalOperationRetainsIdempotentRegistrationWithoutNewAuthority() {
        BrokerCluster cluster = begun();
        List<BrokerMembershipStep> steps = cluster.findOperation(OP).orElseThrow().membershipSteps();
        for (BrokerMembershipStep step : steps) {
            assertThat(register(cluster, step, step.stepId(), cluster.revision()).accepted()).isTrue();
            assertThat(confirm(cluster, step, step.stepId(), BrokerMembershipPermit.Result.TARGET_APPLIED).accepted()).isTrue();
        }
        assertThat(cluster.commitOperation(OP, DIGEST, cluster.revision(), Instant.EPOCH).accepted()).isTrue();
        assertThat(cluster.advanceOperation(OP, DIGEST, BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.SUCCEEDED, null, Instant.EPOCH).accepted()).isTrue();
        BrokerClusterOperation terminal = cluster.findOperation(OP).orElseThrow();
        ClusterRevision revision = cluster.revision();
        assertThat(register(cluster, steps.getFirst(), steps.getFirst().stepId(), new ClusterRevision(1L)).duplicate()).isTrue();
        assertThat(cluster.revision()).isEqualTo(revision);
        assertThat(cluster.findOperation(OP)).contains(terminal);
        assertThat(terminal.membershipSteps().stream().allMatch(BrokerMembershipStep::successful)).isTrue();
        assertThat(register(cluster, steps.getFirst(), "fresh", revision).accepted()).isFalse();
        assertThat(cluster.registerMembershipPermit(OP, steps.getFirst().stepId(), steps.getFirst().stepId(),
                new BrokerId("a"), "different-process", revision).accepted()).isFalse();
        assertThat(cluster.findOperation(OP)).contains(terminal);
    }

    @Test
    void failedTerminalOperationRetainsIdempotentDrainedPermitRegistration() {
        BrokerCluster cluster = membershipCluster(BrokerMembershipRole.DISCOVERED);
        BrokerClusterOperation operation = begin(cluster, BrokerClusterOperationType.START_LEARNER_SYNC);
        BrokerMembershipStep step = operation.currentMembershipStep();
        assertThat(register(cluster, step, "failed-permit", cluster.revision()).accepted()).isTrue();
        assertThat(confirm(cluster, step, "failed-permit", BrokerMembershipPermit.Result.TARGET_NOT_APPLIED).accepted()).isTrue();
        assertThat(cluster.advanceOperation(OP, DIGEST, BrokerClusterOperationPhase.PENDING,
                BrokerClusterOperationPhase.FAILED, new OperationFailureReason("explicit rejection"), Instant.EPOCH).accepted()).isTrue();
        BrokerClusterOperation terminal = cluster.findOperation(OP).orElseThrow();
        ClusterRevision revision = cluster.revision();
        assertThat(register(cluster, step, "failed-permit", new ClusterRevision(1L)).duplicate()).isTrue();
        assertThat(cluster.revision()).isEqualTo(revision);
        assertThat(cluster.findOperation(OP)).contains(terminal);
        assertThat(terminal.currentMembershipStep().currentPermit().result()).isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
        assertThat(register(cluster, step, "fresh", revision).accepted()).isFalse();
        assertThat(cluster.findOperation(OP)).contains(terminal);
    }

    @Test
    void promotionFreezesRemovalBeforeFullVoterTarget() {
        BrokerCluster cluster = membershipCluster(BrokerMembershipRole.LEARNER);
        BrokerClusterOperation operation = begin(cluster, BrokerClusterOperationType.PROMOTE_LEARNER);
        MembershipEndpoint a = new MembershipEndpoint("a:18120");
        MembershipEndpoint b = new MembershipEndpoint("b:18120");
        assertThat(operation.membershipSteps()).hasSize(2);
        assertThat(operation.membershipSteps().getFirst().target()).isEqualTo(
                new ConsensusMembership(java.util.Set.of(a), java.util.Set.of()));
        assertThat(operation.membershipSteps().getLast().target()).isEqualTo(
                new ConsensusMembership(java.util.Set.of(a, b), java.util.Set.of()));
        assertThat(operation.membershipSteps().stream().allMatch(step -> step.permits().isEmpty())).isTrue();
        assertThat(cluster.findNode(new BrokerId("b")).orElseThrow().membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER);
    }

    @Test
    void joinAndRemovalFreezeCompleteTargetsNotEndpointDeltas() {
        MembershipEndpoint a = new MembershipEndpoint("a:18120");
        MembershipEndpoint b = new MembershipEndpoint("b:18120");
        BrokerCluster joining = membershipCluster(BrokerMembershipRole.DISCOVERED);
        BrokerClusterOperation join = begin(joining, BrokerClusterOperationType.START_LEARNER_SYNC);
        assertThat(join.membershipSteps()).hasSize(1);
        assertThat(join.currentMembershipStep().target()).isEqualTo(new ConsensusMembership(java.util.Set.of(a), java.util.Set.of(b)));
        BrokerCluster removing = membershipCluster(BrokerMembershipRole.LEARNER, BrokerTrafficState.INACTIVE);
        BrokerClusterOperation removal = begin(removing, BrokerClusterOperationType.REMOVE_BROKER);
        assertThat(removal.membershipSteps()).hasSize(1);
        assertThat(removal.currentMembershipStep().target()).isEqualTo(new ConsensusMembership(java.util.Set.of(a), java.util.Set.of()));
        assertThat(removing.findNode(new BrokerId("b"))).isPresent();
    }

    @Test
    void learnerSyncCompletionUsesAcceptedRevisionAndRejectsChangedAggregateVersion() {
        BrokerCluster cluster = membershipCluster(BrokerMembershipRole.LEARNER_SYNCING);
        BrokerClusterOperation operation = begin(cluster, BrokerClusterOperationType.COMPLETE_LEARNER_SYNC);
        assertThat(operation.membershipSteps()).isEmpty();
        assertThat(cluster.commitOperation(OP, DIGEST, cluster.revision(), Instant.EPOCH).accepted()).isFalse();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(1L));
        BrokerCluster advanced = BrokerCluster.builder().revision(new ClusterRevision(2L)).epoch(cluster.epoch())
                .algorithm(cluster.algorithm()).brokers(cluster.brokers()).operations(cluster.operations())
                .membershipEndpoints(cluster.membershipEndpoints()).operationHistoryCapacity(new OperationHistoryCapacity(100)).build();
        assertThat(advanced.commitOperation(OP, DIGEST, operation.acceptedRevision(), Instant.EPOCH).accepted()).isFalse();
        assertThat(advanced.revision()).isEqualTo(new ClusterRevision(2L));
        assertThat(advanced.findNode(new BrokerId("b")).orElseThrow().membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(cluster.commitOperation(OP, DIGEST, operation.acceptedRevision(), Instant.EPOCH).accepted()).isTrue();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(2L));
        assertThat(cluster.findNode(new BrokerId("b")).orElseThrow().membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER);
    }

    private BrokerCluster membershipCluster(BrokerMembershipRole role) {
        return membershipCluster(role, role == BrokerMembershipRole.LEARNER ? BrokerTrafficState.READY : BrokerTrafficState.INACTIVE);
    }

    private BrokerCluster membershipCluster(BrokerMembershipRole role, BrokerTrafficState trafficState) {
        return BrokerCluster.builder().revision(new ClusterRevision(0L)).epoch(new RoutingEpoch(0L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(new BrokerNode(new BrokerId("a"), new BrokerRouteEndpoint("a:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE),
                        new BrokerNode(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"), role,
                                trafficState)))
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(role == BrokerMembershipRole.DISCOVERED
                        ? Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"))
                        : Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"),
                                new BrokerId("b"), new MembershipEndpoint("b:18120"))).build();
    }

    private BrokerClusterOperation begin(BrokerCluster cluster, BrokerClusterOperationType type) {
        BrokerClusterDecision decision = cluster.submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(OP).commandDigest(DIGEST).expectedRevision(cluster.revision()).operationType(type)
                .targetBrokerId(new BrokerId("b")).changedAt(Instant.EPOCH)
                .membershipEndpoint(type == BrokerClusterOperationType.START_LEARNER_SYNC ? new MembershipEndpoint("b:18120") : null)
                .minimumVoterCount(type == BrokerClusterOperationType.REMOVE_BROKER ? new MinimumVoterCount(1) : null).build());
        assertThat(decision.accepted()).isTrue();
        return decision.operation();
    }

    private BrokerClusterDecision register(BrokerCluster cluster, BrokerMembershipStep step, String id, ClusterRevision revision) {
        return cluster.registerMembershipPermit(OP, step.stepId(), id, new BrokerId("a"), "incarnation", revision);
    }

    private BrokerClusterDecision confirm(BrokerCluster cluster, BrokerMembershipStep step, String id, BrokerMembershipPermit.Result result) {
        return cluster.confirmMembershipExecution(new MembershipExecutionConfirmation(
                OP, step.stepId(), id, new BrokerId("a"), "incarnation", step.targetDigest(), result));
    }
}
