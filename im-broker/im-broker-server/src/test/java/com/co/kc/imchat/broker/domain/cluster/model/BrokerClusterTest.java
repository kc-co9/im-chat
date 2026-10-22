package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.gatewayId;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeCount;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeRevision;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.snapshotDigest;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.summary;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;

class BrokerClusterTest {

    @Test
    void drainCleanupRequiresConfirmedGatewaysAndStopsAfterEvidenceIsSaved() {
        DrainProgress progress = new DrainProgress(List.of(checkpoint("gateway-a", 900L)), at(2_000L), null);
        BrokerClusterOperation operation = BrokerClusterOperation.builder()
                .id(new ClusterOperationId("drain-qualification")).commandDigest(digest("drain-digest"))
                .type(BrokerClusterOperationType.DRAIN_BROKER).targetBrokerId(new BrokerId("broker-b"))
                .acceptedEpoch(new RoutingEpoch(3L)).acceptedRevision(new ClusterRevision(1L))
                .resultEpoch(new RoutingEpoch(4L)).phase(BrokerClusterOperationPhase.REBUILDING_ROUTES)
                .createdAt(at(1_000L)).drainProgress(progress).build();

        assertThat(operation.needsRouteCleanup()).isFalse();
        assertThat(operation.waitingForGatewayAcks(at(10_999L), Duration.ofSeconds(10))).isTrue();
        assertThat(operation.waitingForGatewayAcks(at(11_000L), Duration.ofSeconds(10))).isFalse();
        assertThat(operation.timedOut(at(11_000L), Duration.ofSeconds(10))).isTrue();

        DrainProgress confirmed = progress.confirmGateway(gatewayId("gateway-a"), routeRevision(7L),
                routeCount(3L), snapshotDigest("gateway-a"));
        BrokerClusterOperation ready = operation.updateDrainProgress(confirmed);
        assertThat(ready.needsRouteCleanup()).isTrue();
        List<DrainProgressUpdate> confirmations = List.of(DrainProgressUpdate.confirmGateway(
                gatewayId("gateway-a"), routeRevision(7L), routeCount(3L), snapshotDigest("gateway-a")));
        assertThat(ready.matchesRouteCleanup(new BrokerId("broker-b"), new RoutingEpoch(3L),
                new RoutingEpoch(4L), at(2_000L), confirmations)).isTrue();
        assertThat(ready.matchesRouteCleanup(new BrokerId("broker-a"), new RoutingEpoch(3L),
                new RoutingEpoch(4L), at(2_000L), confirmations)).isFalse();
        assertThat(ready.matchesRouteCleanup(new BrokerId("broker-b"), new RoutingEpoch(3L),
                new RoutingEpoch(4L), at(2_001L), confirmations)).isFalse();
        assertThat(ready.waitingForGatewayAcks(at(1_500L), Duration.ofSeconds(10))).isFalse();
        BrokerClusterOperation cleaned = ready.updateDrainProgress(confirmed.summarize(summary(3L, 2L, 0L, true)));
        assertThat(cleaned.needsRouteCleanup()).isFalse();
        assertThat(cleaned.advance(BrokerClusterOperationPhase.VERIFYING, null, at(2_100L)).needsRouteCleanup()).isFalse();
    }

    @Test
    void genericPhaseChangeCannotBypassRecoveryVerification() {
        BrokerCluster cluster = failureRecoveryCluster(new DrainProgress(
                List.of(checkpoint("gateway-a", 900L)), at(2_000L), summary(0L, 0L, 0L, false)));
        BrokerClusterOperation before = cluster.findOperation(new ClusterOperationId("failure-1")).orElseThrow();

        BrokerClusterDecision decision = cluster.advanceOperation(before.id(), before.commandDigest(),
                BrokerClusterOperationPhase.REBUILDING_ROUTES, BrokerClusterOperationPhase.VERIFYING,
                failureReason("force degradation before timeout"), at(1_500L));

        assertThat(decision.accepted()).isFalse();
        assertThat(cluster.findOperation(before.id())).contains(before);
    }

    @Test
    void verificationRequiresEvidenceAndElapsedTimeoutBeforeDegradation() {
        BrokerCluster missingEvidence = failureRecoveryCluster(new DrainProgress(
                List.of(checkpoint("gateway-a", 900L)), at(2_000L), null));
        assertThat(missingEvidence.verifyRouteRecovery(new ClusterOperationId("failure-1"), digest("failure-digest"),
                Duration.ofSeconds(10), at(12_000L)).accepted()).isFalse();

        BrokerCluster cluster = failureRecoveryCluster(new DrainProgress(
                List.of(checkpoint("gateway-a", 900L)), at(2_000L), summary(0L, 0L, 0L, false)));
        BrokerClusterOperation before = cluster.findOperation(new ClusterOperationId("failure-1")).orElseThrow();
        assertThat(cluster.verifyRouteRecovery(before.id(), before.commandDigest(), Duration.ZERO, at(1_500L)).accepted())
                .isFalse();
        assertThat(before.waitingForGatewayAcks(at(10_999L), Duration.ofSeconds(10))).isTrue();
        assertThat(cluster.verifyRouteRecovery(before.id(), before.commandDigest(),
                Duration.ofSeconds(10), at(10_999L)).accepted()).isFalse();
        assertThat(cluster.findOperation(before.id())).contains(before);

        BrokerClusterDecision verified = cluster.verifyRouteRecovery(before.id(), before.commandDigest(),
                Duration.ofSeconds(10), at(11_000L));
        assertThat(verified.accepted()).isTrue();
        assertThat(verified.operation().phase()).isEqualTo(BrokerClusterOperationPhase.VERIFYING);
        assertThat(verified.operation().error().value()).contains("gateway-a");
    }

    @Test
    void completionUsesPersistedDegradationAndIsIdempotent() {
        BrokerCluster cluster = failureRecoveryCluster(new DrainProgress(
                List.of(checkpoint("gateway-a", 900L)), at(2_000L), summary(0L, 0L, 0L, false)));
        ClusterOperationId id = new ClusterOperationId("failure-1");
        CommandDigest digest = digest("failure-digest");
        BrokerClusterDecision verified = cluster.verifyRouteRecovery(id, digest, Duration.ofSeconds(10), at(11_000L));
        assertThat(cluster.verifyRouteRecovery(id, digest, Duration.ofDays(1), at(11_001L)).status())
                .isEqualTo(BrokerClusterDecision.Status.DUPLICATE);

        BrokerClusterDecision completed = cluster.completeRouteRecovery(id, digest, at(11_002L));
        assertThat(completed.accepted()).isTrue();
        assertThat(completed.operation().phase()).isEqualTo(BrokerClusterOperationPhase.DEGRADED);
        assertThat(completed.operation().error()).isEqualTo(verified.operation().error());
        assertThat(cluster.completeRouteRecovery(id, digest, at(12_000L)).status())
                .isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
        assertThat(cluster.findOperation(id)).contains(completed.operation());
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(2L));
    }

    @Test
    void normalRecoveryRequiresOldEventsAndMatchingRebuildEvidence() {
        DrainProgress confirmed = new DrainProgress(List.of(checkpoint("gateway-a", 900L)), at(2_000L), null)
                .confirmGateway(gatewayId("gateway-a"), routeRevision(7L), routeCount(3L), snapshotDigest("gateway-a"));
        BrokerCluster invalid = failureRecoveryCluster(confirmed.summarize(summary(2L, 0L, 0L, true)));
        ClusterOperationId id = new ClusterOperationId("failure-1");
        CommandDigest digest = digest("failure-digest");
        assertThat(invalid.verifyRouteRecovery(id, digest, Duration.ofSeconds(10), at(2_100L)).accepted()).isFalse();

        BrokerCluster cluster = failureRecoveryCluster(confirmed.summarize(confirmed.summarizeRebuild()));
        assertThat(cluster.verifyRouteRecovery(id, digest, Duration.ofSeconds(10), at(1_999L)).accepted()).isFalse();
        assertThat(cluster.completeRouteRecovery(id, digest, at(1_999L)).accepted()).isFalse();
        assertThat(cluster.verifyRouteRecovery(id, digest, Duration.ofSeconds(10), at(2_000L)).accepted()).isTrue();
        BrokerClusterDecision completed = cluster.completeRouteRecovery(id, digest, at(2_001L));
        assertThat(completed.operation().phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(completed.operation().error()).isNull();
    }

    @Test
    void staleRebuildEvidenceCannotFreezeNewlyConfirmedGateway() {
        BrokerCluster cluster = failureRecoveryCluster(new DrainProgress(
                List.of(checkpoint("gateway-a", 900L)), at(2_000L), null));
        ClusterOperationId id = new ClusterOperationId("failure-1");
        CommandDigest digest = digest("failure-digest");
        RouteRecoveryEvidence stale = cluster.findOperation(id).orElseThrow().drainProgress().summarizeRebuild();
        assertThat(cluster.updateDrainProgress(id, digest, DrainProgressUpdate.confirmGateway(
                gatewayId("gateway-a"), routeRevision(7L), routeCount(3L), snapshotDigest("gateway-a")), at(1_500L))
                .accepted()).isTrue();

        assertThat(cluster.updateDrainProgress(id, digest, DrainProgressUpdate.summarize(stale), at(12_000L)).accepted())
                .isFalse();
        DrainProgress current = cluster.findOperation(id).orElseThrow().drainProgress();
        assertThat(current.recoveryEvidence()).isNull();
        assertThat(current.allGatewaysConfirmed()).isTrue();
        assertThat(cluster.updateDrainProgress(id, digest, DrainProgressUpdate.summarize(current.summarizeRebuild()), at(12_001L))
                .accepted()).isTrue();
        assertThat(cluster.completeRouteRecovery(id, digest, at(12_002L)).operation().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
    }

    private BrokerCluster failureRecoveryCluster(DrainProgress progress) {
        BrokerClusterOperation operation = BrokerClusterOperation.builder()
                .id(new ClusterOperationId("failure-1")).commandDigest(digest("failure-digest"))
                .type(BrokerClusterOperationType.FAIL_BROKER).targetBrokerId(new BrokerId("broker-b"))
                .acceptedEpoch(new RoutingEpoch(3L)).acceptedRevision(new ClusterRevision(1L))
                .resultEpoch(new RoutingEpoch(4L)).phase(BrokerClusterOperationPhase.REBUILDING_ROUTES)
                .createdAt(at(1_000L)).drainProgress(progress).build();
        return clusterBuilder().epoch(new RoutingEpoch(4L)).revision(new ClusterRevision(2L))
                .brokers(List.of(activeNode(), withTraffic(secondActiveNode(), BrokerTrafficState.INACTIVE)))
                .operations(List.of(operation)).build();
    }

    @Test
    void leaseAdmissionUsesOwnedTopologyAndPreservesRejectionReasons() {
        BrokerCluster cluster = clusterWithReadyBroker();

        assertThat(cluster.checkLeaseGrant(activeNode().id(), cluster.epoch())).isEmpty();
        LeaseGrantResult stale = cluster.checkLeaseGrant(activeNode().id(), new RoutingEpoch(2L)).orElseThrow();
        assertThat(stale.status()).isEqualTo(LeaseGrantResult.Status.DENIED);
        assertThat(stale.reason().value()).isEqualTo("lease request does not match committed state");
        LeaseGrantResult inactive = cluster.checkLeaseGrant(new BrokerId("broker-b"), cluster.epoch()).orElseThrow();
        assertThat(inactive.status()).isEqualTo(LeaseGrantResult.Status.DENIED);
        assertThat(inactive.reason().value()).isEqualTo("broker is not ACTIVE in committed state");
        assertThat(cluster.checkLeaseGrant(new BrokerId("missing"), cluster.epoch()).orElseThrow().status())
                .isEqualTo(LeaseGrantResult.Status.DENIED);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(1L));
        assertThat(cluster.operations()).isEmpty();
        assertThat(cluster.findNode(activeNode().id()).orElseThrow().trafficState()).isEqualTo(BrokerTrafficState.ACTIVE);
    }

    @Test
    void leaseAdmissionRejectsClosedEpochButAllowsNewEpochWithHistoricalFence() {
        RoutingEpochFence fence = RoutingEpochFence.builder()
                .operationId(new ClusterOperationId("fence-3")).epoch(new RoutingEpoch(3L))
                .leaderTerm(term(1L)).quorumReadIndex(readIndex(10L))
                .fencedBrokerIds(List.of(activeNode().id())).build();
        BrokerCluster closed = clusterBuilder().routingFence(fence).build();

        LeaseGrantResult rejection = closed.checkLeaseGrant(activeNode().id(), closed.epoch()).orElseThrow();
        assertThat(rejection.status()).isEqualTo(LeaseGrantResult.Status.FENCED);
        assertThat(rejection.reason().value()).isEqualTo("epoch grant is closed");
        assertThat(closed.findRoutingFence()).contains(fence);
        assertThat(closed.operations()).isEmpty();

        BrokerCluster nextEpoch = clusterBuilder().epoch(new RoutingEpoch(4L)).routingFence(fence).build();
        assertThat(nextEpoch.checkLeaseGrant(activeNode().id(), nextEpoch.epoch())).isEmpty();
        assertThat(nextEpoch.findRoutingFence()).contains(fence);
    }

    @Test
    void nodeCanDeactivateButAggregateProtectsLastActiveBroker() {
        BrokerCluster cluster = cluster();
        BrokerNode node = cluster.findNode(activeNode().id()).orElseThrow();
        assertThat(node.checkOperation(BrokerClusterOperationType.DEACTIVATE_BROKER)).isEmpty();

        BrokerClusterDecision decision = cluster.deactivate(BrokerDeactivationIntent.builder()
                .operationId(new ClusterOperationId("deactivate-last"))
                .commandDigest(digest("deactivate-digest"))
                .expectedRevision(cluster.revision()).brokerId(node.id()).changedAt(at(1_000L)).build());

        assertThat(decision.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(decision.reason().value()).contains("at least one ACTIVE broker");
        assertThat(cluster.operations()).isEmpty();
        assertThat(cluster.findNode(node.id()).orElseThrow().trafficState()).isEqualTo(BrokerTrafficState.ACTIVE);
    }

    @Test
    void nodeCanDemoteButAggregateProtectsMinimumVoterCount() {
        BrokerCluster cluster = clusterWithTwoActiveBrokers();
        BrokerNode target = cluster.findNode(secondActiveNode().id()).orElseThrow();
        assertThat(target.checkOperation(BrokerClusterOperationType.DEMOTE_VOTER)).isEmpty();

        BrokerClusterDecision decision = cluster.demoteVoter(VoterDemotionIntent.builder()
                .operationId(new ClusterOperationId("demote-1")).commandDigest(digest("demote-digest"))
                .expectedRevision(cluster.revision()).brokerId(target.id())
                .minimumVoterCount(new MinimumVoterCount(3)).changedAt(at(1_000L)).build());

        assertThat(decision.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(decision.reason().value()).contains("minimum voter count 3");
        assertThat(cluster.operations()).isEmpty();
        assertThat(cluster.findNode(target.id()).orElseThrow().membershipRole()).isEqualTo(BrokerMembershipRole.VOTER);
    }

    @ParameterizedTest
    @EnumSource(value = BrokerMembershipRole.class, mode = EnumSource.Mode.EXCLUDE,
            names = "DISCOVERED")
    void newLearnerJoinRejectsOtherMembershipRolesWithoutRegisteringOperation(BrokerMembershipRole role) {
        BrokerId brokerId = new BrokerId("broker-b");
        BrokerCluster cluster = clusterBuilder().brokers(List.of(activeNode(),
                new BrokerNode(brokerId, new BrokerRouteEndpoint("10.0.0.2:18020"),
                        role, BrokerTrafficState.INACTIVE))).build();
        ClusterRevision revision = cluster.revision();
        LearnerJoinIntent intent = LearnerJoinIntent.builder()
                .operationId(new ClusterOperationId("join-1"))
                .commandDigest(digest("join-digest"))
                .expectedRevision(revision).brokerId(brokerId)
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(at(1_000L)).build();

        BrokerClusterDecision decision = cluster.addLearner(intent);

        assertThat(decision.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(decision.reason().value()).isEqualTo("broker membership must be DISCOVERED");
        assertThat(decision.duplicate()).isFalse();
        assertThat(cluster.findOperation(intent.operationId())).isEmpty();
        assertThat(cluster.revision()).isEqualTo(revision);
        assertThat(cluster.findNode(brokerId).orElseThrow().membershipRole()).isEqualTo(role);
    }

    @Test
    void learnerProposalDoesNotChangeCommittedNodeOrEarlierSnapshot() {
        BrokerNode discovered = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"));
        BrokerCluster cluster = clusterBuilder().brokers(List.of(activeNode(), discovered)).build();
        BrokerCluster snapshot = cluster.snapshot();

        BrokerClusterDecision decision = cluster.addLearner(LearnerJoinIntent.builder()
                .operationId(new ClusterOperationId("join-1"))
                .commandDigest(digest("join-digest"))
                .expectedRevision(cluster.revision())
                .brokerId(discovered.id())
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(at(1_000L))
                .build());

        assertThat(decision.accepted()).isTrue();
        assertThat(cluster.findNode(discovered.id()).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
        assertThat(snapshot.findNode(discovered.id()).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
        assertThat(decision.operation().targetBrokerId()).isEqualTo(discovered.id());
        assertThat(decision.operation().membershipEndpoint()).isEqualTo(new MembershipEndpoint("10.0.0.2:18120"));

        assertThat(cluster.commitOperation(decision.operation().id(),
                decision.operation().commandDigest(), decision.operation().acceptedRevision(),
                at(1_100L)).accepted()).isTrue();
        assertThat(cluster.findNode(discovered.id()).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(snapshot.findNode(discovered.id()).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
    }

    @ParameterizedTest
    @EnumSource(value = BrokerClusterOperationType.class, names = {
            "ACTIVATE_BROKER", "DEACTIVATE_BROKER", "DRAIN_BROKER", "FAIL_BROKER",
            "START_LEARNER_SYNC", "COMPLETE_LEARNER_SYNC", "PROMOTE_LEARNER",
            "DEMOTE_VOTER", "REMOVE_BROKER"})
    void committedBusinessOperationCanBeRetriedAfterTargetStateChanges(BrokerClusterOperationType type) {
        BrokerMembershipRole role = switch (type) {
            case START_LEARNER_SYNC -> BrokerMembershipRole.DISCOVERED;
            case COMPLETE_LEARNER_SYNC -> BrokerMembershipRole.LEARNER_SYNCING;
            case PROMOTE_LEARNER -> BrokerMembershipRole.LEARNER;
            default -> BrokerMembershipRole.VOTER;
        };
        BrokerTrafficState traffic = switch (type) {
            case ACTIVATE_BROKER -> BrokerTrafficState.READY;
            case DEACTIVATE_BROKER, DRAIN_BROKER, FAIL_BROKER -> BrokerTrafficState.ACTIVE;
            default -> BrokerTrafficState.INACTIVE;
        };
        BrokerCluster cluster = clusterBuilder().brokers(List.of(activeNode(),
                new BrokerNode(new BrokerId("broker-b"), new BrokerRouteEndpoint("10.0.0.2:18020"),
                        role, traffic))).build();
        BrokerClusterDecision begun = businessChange(cluster, type, digest("retry-digest"));
        assertThat(begun.accepted()).isTrue();
        assertThat(begun.duplicate()).isFalse();
        BrokerClusterOperation operation = begun.operation();
        if (!operation.resultEpoch().equals(operation.acceptedEpoch())) {
            assertThat(cluster.commitRoutingFence(fence("retry", 7L, 21L,
                    cluster.brokers().stream().filter(node -> node.trafficState() == BrokerTrafficState.ACTIVE)
                            .map(BrokerNode::id).toList())).accepted()).isTrue();
        }
        ClusterRevision revisionBeforeCommit = cluster.revision();
        RoutingEpoch epochBeforeCommit = cluster.epoch();
        assertThat(commitOperation(cluster, proposalFor(operation)).accepted()).isTrue();
        assertThat(cluster.revision()).isEqualTo(revisionBeforeCommit.next());
        assertThat(cluster.epoch()).isEqualTo(operation.resultEpoch());
        if (type == BrokerClusterOperationType.ACTIVATE_BROKER
                || type == BrokerClusterOperationType.DEACTIVATE_BROKER
                || type == BrokerClusterOperationType.DRAIN_BROKER
                || type == BrokerClusterOperationType.FAIL_BROKER) {
            assertThat(cluster.epoch()).isNotEqualTo(epochBeforeCommit);
        }
        ClusterRevision committedRevision = cluster.revision();

        BrokerClusterDecision retry = businessChange(cluster, type, digest("retry-digest"));
        assertThat(retry.status()).isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
        assertThat(retry.duplicate()).isTrue();
        assertThat(retry.operation()).isEqualTo(cluster.findOperation(operation.id()).orElseThrow());
        assertThat(cluster.revision()).isEqualTo(committedRevision);
        assertThat(businessChange(cluster, type, digest("different-digest")).status())
                .isEqualTo(BrokerClusterDecision.Status.REJECTED);
    }

    @Test
    void failedUncommittedOperationCannotBeReportedAsCommitted() {
        BrokerCluster cluster = clusterBuilder().brokers(List.of(activeNode(),
                BrokerNode.discovered(new BrokerId("broker-b"),
                        new BrokerRouteEndpoint("10.0.0.2:18020")))).build();
        BrokerClusterOperation operation = businessChange(cluster,
                BrokerClusterOperationType.START_LEARNER_SYNC, digest("retry-digest")).operation();
        assertThat(cluster.advanceOperation(operation.id(), operation.commandDigest(),
                BrokerClusterOperationPhase.PENDING, BrokerClusterOperationPhase.FAILED,
                failureReason("membership rejected"), at(1_100L)).accepted()).isTrue();

        assertThat(commitOperation(cluster, proposalFor(operation)).status())
                .isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(cluster.findNode(new BrokerId("broker-b")).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
    }

    @ParameterizedTest
    @EnumSource(value = BrokerClusterOperationPhase.class, names = {"REBUILDING_ROUTES", "VERIFYING"})
    void routeRecoveryBlocksNextTopologyUntilTerminal(BrokerClusterOperationPhase phase) {
        BrokerCluster cluster = clusterWithTwoActiveBrokers();
        BrokerClusterOperation operation = businessChange(cluster,
                BrokerClusterOperationType.DRAIN_BROKER, digest("retry-digest")).operation();
        cluster.commitRoutingFence(fence("retry", 7L, 21L,
                List.of(activeNode().id(), secondActiveNode().id())));
        assertThat(commitOperation(cluster, proposalFor(operation)).accepted()).isTrue();
        assertThat(cluster.advanceOperation(operation.id(), operation.commandDigest(),
                BrokerClusterOperationPhase.RAFT_COMMITTED, BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                null, at(1_200L)).accepted()).isTrue();
        assertThat(cluster.advanceOperation(operation.id(), operation.commandDigest(),
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, BrokerClusterOperationPhase.REBUILDING_ROUTES,
                null, at(1_300L)).accepted()).isTrue();
        if (phase == BrokerClusterOperationPhase.VERIFYING) {
            assertThat(cluster.advanceOperation(operation.id(), operation.commandDigest(),
                    BrokerClusterOperationPhase.REBUILDING_ROUTES, phase, null, at(1_400L)).accepted()).isTrue();
        }
        BrokerClusterOperationProposal next = topologyChange("next", cluster.revision(),
                List.of(activeNode(), withTraffic(secondActiveNode(), BrokerTrafficState.DRAINING),
                        BrokerNode.discovered(new BrokerId("broker-c"), new BrokerRouteEndpoint("10.0.0.3:18020"))));
        assertThat(cluster.submitOperation(next).status()).isEqualTo(BrokerClusterDecision.Status.CONFLICT);
        assertThat(cluster.advanceOperation(operation.id(), operation.commandDigest(), phase,
                BrokerClusterOperationPhase.FAILED, failureReason("recovery stopped"), at(1_500L)).accepted()).isTrue();
        assertThat(cluster.submitOperation(next).status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
    }

    private BrokerClusterDecision businessChange(BrokerCluster cluster, BrokerClusterOperationType type,
                                                CommandDigest digest) {
        ClusterOperationId id = new ClusterOperationId("retry");
        BrokerId target = new BrokerId("broker-b");
        ClusterRevision revision = new ClusterRevision(1L);
        Instant changedAt = at(1_000L);
        DrainProgress progress = new DrainProgress(List.of(), at(2_000L), null);
        return switch (type) {
            case ACTIVATE_BROKER -> cluster.activate(BrokerActivationIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .changedAt(changedAt)
                    .build());
            case DEACTIVATE_BROKER -> cluster.deactivate(BrokerDeactivationIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .changedAt(changedAt)
                    .build());
            case DRAIN_BROKER -> cluster.drain(BrokerDrainIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .progress(progress)
                    .changedAt(changedAt)
                    .build());
            case FAIL_BROKER -> cluster.removeFailedBroker(BrokerFailureRemovalIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .progress(progress)
                    .changedAt(changedAt)
                    .build());
            case START_LEARNER_SYNC -> cluster.addLearner(LearnerJoinIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                    .changedAt(changedAt)
                    .build());
            case COMPLETE_LEARNER_SYNC -> cluster.confirmLearner(LearnerSyncCompletionIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .changedAt(changedAt)
                    .build());
            case PROMOTE_LEARNER -> cluster.promoteLearner(LearnerPromotionIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .changedAt(changedAt)
                    .build());
            case DEMOTE_VOTER -> cluster.demoteVoter(VoterDemotionIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .minimumVoterCount(new MinimumVoterCount(1))
                    .changedAt(changedAt)
                    .build());
            case REMOVE_BROKER -> cluster.removeBroker(BrokerRemovalIntent.builder()
                    .operationId(id)
                    .commandDigest(digest)
                    .expectedRevision(revision)
                    .brokerId(target)
                    .minimumVoterCount(new MinimumVoterCount(1))
                    .changedAt(changedAt)
                    .build());
            default -> throw new IllegalArgumentException("unsupported test operation");
        };
    }

    private BrokerClusterOperationProposal proposalFor(BrokerClusterOperation operation) {
        return BrokerClusterOperationProposal.builder().operationId(operation.id())
                .commandDigest(operation.commandDigest()).expectedRevision(operation.acceptedRevision())
                .operationType(operation.type()).targetBrokerId(operation.targetBrokerId())
                .routeEndpoint(operation.routeEndpoint()).minimumVoterCount(operation.minimumVoterCount()).changedAt(at(1_100L))
                .membershipEndpoint(operation.membershipEndpoint())
                .initialDrainProgress(operation.drainProgress()).build();
    }

    private BrokerClusterDecision commitOperation(BrokerCluster cluster,
                                                 BrokerClusterOperationProposal proposal) {
        return cluster.commitOperation(proposal.operationId(), proposal.commandDigest(),
                proposal.expectedRevision(), proposal.changedAt());
    }

    @Test
    void activationUsesBusinessMethodsWithoutCallerBuiltTopology() {
        BrokerCluster cluster = clusterWithReadyBroker();
        ClusterOperationId operationId = new ClusterOperationId("activate-broker-b");
        CommandDigest commandDigest = digest("activate-digest");

        BrokerClusterDecision begun = cluster.activate(BrokerActivationIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(new ClusterRevision(1L))
                .brokerId(new BrokerId("broker-b"))
                .changedAt(at(1_000L))
                .build());

        assertThat(begun.status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(begun.operation().phase())
                .isEqualTo(BrokerClusterOperationPhase.FENCING_OLD_EPOCH);
        assertThat(cluster.findNode(new BrokerId("broker-b")).orElseThrow().trafficState())
                .isEqualTo(BrokerTrafficState.READY);

        cluster.commitRoutingFence(fence("activate-broker-b", 7L, 21L,
                List.of(activeNode().id())));
        BrokerClusterOperationProposal committedOperation = BrokerClusterOperationProposal.builder()
                .operationId(operationId).commandDigest(commandDigest)
                .expectedRevision(begun.operation().acceptedRevision())
                .operationType(begun.operation().type())
                .targetBrokerId(begun.operation().targetBrokerId())
                .routeEndpoint(begun.operation().routeEndpoint()).changedAt(at(1_100L)).build();
        BrokerClusterDecision activated = commitOperation(cluster, committedOperation);

        assertThat(activated.status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(cluster.findNode(new BrokerId("broker-b")))
                .get().extracting(BrokerNode::trafficState)
                .isEqualTo(BrokerTrafficState.ACTIVE);
    }

    @Test
    void topologyChangeOwnsRevisionEpochAndOperationIdempotency() {
        BrokerCluster cluster = cluster();
        BrokerNode second = BrokerNode.discovered(
                new BrokerId("broker-b"), new BrokerRouteEndpoint("10.0.0.2:18020"));
        BrokerClusterOperationProposal change = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("operation-1"))
                .commandDigest(digest("digest-1"))
                .expectedRevision(new ClusterRevision(1L))
                .operationType(BrokerClusterOperationType.ADD_BROKER)
                .targetBrokerId(second.id())
                .routeEndpoint((List.of(activeNode(), second)).stream().filter(node -> node.id().equals(second.id()))
                        .findFirst().map(BrokerNode::routeEndpoint).orElse(null))
                .changedAt(at(1_000L))
                .build();

        BrokerClusterDecision begun = cluster.submitOperation(change);
        BrokerClusterDecision applied = commitOperation(cluster, change);
        BrokerClusterDecision duplicate = commitOperation(cluster, change);

        assertThat(begun.operation().phase()).isEqualTo(BrokerClusterOperationPhase.PENDING);
        assertThat(applied.status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(3L));
        assertThat(cluster.epoch()).isEqualTo(new RoutingEpoch(3L));
        assertThat(cluster.findOperation(new ClusterOperationId("operation-1")))
                .get().extracting(BrokerClusterOperation::phase)
                .isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);
        assertThat(duplicate.status()).isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
    }

    @Test
    void topologyCommitUsesFrozenOperationAndRejectsWrongIdentityOrRevision() {
        BrokerCluster cluster = cluster();
        BrokerNode second = BrokerNode.discovered(
                new BrokerId("broker-b"), new BrokerRouteEndpoint("10.0.0.2:18020"));
        BrokerClusterOperationProposal change = topologyChange("operation-1",
                cluster.revision(), List.of(activeNode(), second));
        assertThat(cluster.submitOperation(change).accepted()).isTrue();

        assertThat(cluster.commitOperation(new ClusterOperationId("missing"),
                change.commandDigest(), change.expectedRevision(), at(1_100L)).status())
                .isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(cluster.commitOperation(change.operationId(), digest("different"),
                change.expectedRevision(), at(1_100L)).status())
                .isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(cluster.commitOperation(change.operationId(), change.commandDigest(),
                new ClusterRevision(0L), at(1_100L)).status())
                .isEqualTo(BrokerClusterDecision.Status.CONFLICT);
        assertThat(cluster.brokers()).containsExactly(activeNode());

        assertThat(cluster.commitOperation(change.operationId(), change.commandDigest(),
                change.expectedRevision(), at(1_100L)).status())
                .isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(cluster.brokers()).containsExactly(activeNode(), second);
    }

    @Test
    void restoredSnapshotRejectsAnotherChangeUntilPendingTopologyIsCommitted() {
        BrokerCluster cluster = cluster();
        BrokerNode second = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"));
        BrokerClusterOperationProposal first = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("first"))
                .commandDigest(digest("first-digest"))
                .expectedRevision(cluster.revision())
                .operationType(BrokerClusterOperationType.ADD_BROKER)
                .targetBrokerId(second.id()).routeEndpoint((List.of(activeNode(), second)).stream().filter(node -> node.id().equals(second.id()))
                        .findFirst().map(BrokerNode::routeEndpoint).orElse(null))
                .changedAt(at(1_000L)).build();
        assertThat(cluster.submitOperation(first).accepted()).isTrue();

        BrokerCluster restored = cluster.snapshot();
        BrokerClusterOperationProposal next = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("second"))
                .commandDigest(digest("second-digest"))
                .expectedRevision(restored.revision())
                .operationType(BrokerClusterOperationType.ADD_BROKER)
                .targetBrokerId(second.id()).routeEndpoint((List.of(activeNode(), second)).stream().filter(node -> node.id().equals(second.id()))
                        .findFirst().map(BrokerNode::routeEndpoint).orElse(null))
                .changedAt(at(1_100L)).build();

        assertThat(restored.submitOperation(next).status())
                .isEqualTo(BrokerClusterDecision.Status.CONFLICT);
        assertThat(restored.submitOperation(first).status())
                .isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
        assertThat(restored.operations()).hasSize(1);
        assertThat(commitOperation(restored, first).accepted()).isTrue();
    }

    @Test
    void operationPhaseAndFenceRulesBelongToAggregate() {
        BrokerCluster cluster = cluster();
        BrokerNode second = BrokerNode.discovered(
                new BrokerId("broker-b"), new BrokerRouteEndpoint("10.0.0.2:18020"));
        BrokerClusterOperationProposal change = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("operation-1"))
                .commandDigest(digest("digest-1"))
                .expectedRevision(new ClusterRevision(1L))
                .operationType(BrokerClusterOperationType.ADD_BROKER)
                .targetBrokerId(second.id())
                .routeEndpoint((List.of(activeNode(), second)).stream().filter(node -> node.id().equals(second.id()))
                        .findFirst().map(BrokerNode::routeEndpoint).orElse(null))
                .changedAt(at(1_000L))
                .build();
        cluster.submitOperation(change);
        commitOperation(cluster, change);

        BrokerClusterDecision advanced = cluster.advanceOperation(
                new ClusterOperationId("operation-1"), digest("digest-1"),
                BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                (OperationFailureReason) null, at(1_100L));
        assertThat(advanced.status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
    }

    @Test
    void newBrokerStartsDiscoveredAndExistingEndpointCannotBeChanged() {
        BrokerCluster cluster = cluster();
        BrokerClusterDecision endpointResult = cluster.submitOperation(
                BrokerClusterOperationProposal.builder()
                        .operationId(new ClusterOperationId("operation-endpoint"))
                        .commandDigest(digest("endpoint-digest"))
                        .expectedRevision(cluster.revision())
                        .operationType(BrokerClusterOperationType.ACTIVATE_BROKER)
                        .targetBrokerId(activeNode().id())
                        .routeEndpoint(new BrokerRouteEndpoint("10.0.0.9:18020"))
                        .changedAt(at(1_000L)).build());
        assertThat(endpointResult.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(1L));

        BrokerClusterOperation operation = cluster.submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("add-1")).commandDigest(digest("add-digest"))
                .expectedRevision(cluster.revision()).operationType(BrokerClusterOperationType.ADD_BROKER)
                .targetBrokerId(new BrokerId("broker-b"))
                .routeEndpoint(new BrokerRouteEndpoint("10.0.0.2:18020"))
                .changedAt(at(1_000L)).build()).operation();
        assertThat(cluster.findNode(operation.targetBrokerId())).isEmpty();
        assertThat(cluster.commitOperation(operation.id(), operation.commandDigest(), operation.acceptedRevision(),
                at(1_100L)).accepted()).isTrue();
        BrokerNode added = cluster.findNode(operation.targetBrokerId()).orElseThrow();
        assertThat(added.membershipRole()).isEqualTo(BrokerMembershipRole.DISCOVERED);
        assertThat(added.trafficState()).isEqualTo(BrokerTrafficState.INACTIVE);
        assertThat(cluster.epoch()).isEqualTo(new RoutingEpoch(3L));
    }

    @Test
    void noEffectTopologyChangeKeepsRevisionAndEpoch() {
        BrokerCluster cluster = cluster();

        BrokerClusterDecision result = cluster.submitOperation(
                topologyChange("operation-no-effect", new ClusterRevision(1L),
                        List.of(activeNode())));

        assertThat(result.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(result.reason().value()).contains("no effect");
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(1L));
        assertThat(cluster.epoch()).isEqualTo(new RoutingEpoch(3L));
        assertThat(cluster.operations()).isEmpty();
    }

    @Test
    void rejectsActivationIntentForUnknownBroker() {
        BrokerCluster cluster = cluster();
        BrokerNode discovered = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"));

        BrokerClusterDecision result = cluster.submitOperation(
                BrokerClusterOperationProposal.builder()
                        .operationId(new ClusterOperationId("operation-mismatch"))
                        .commandDigest(digest("digest-mismatch"))
                        .expectedRevision(new ClusterRevision(1L))
                        .operationType(BrokerClusterOperationType.ACTIVATE_BROKER)
                        .targetBrokerId(discovered.id())

                        .changedAt(at(1_000L))
                        .build());

        assertThat(result.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(result.reason().value()).contains("target broker does not exist");
        assertThat(cluster.operations()).isEmpty();
    }

    @Test
    void rejectsActiveBrokerChangeBeforeOldEpochFenceIsCommitted() {
        BrokerCluster cluster = clusterWithTwoActiveBrokers();
        BrokerNode draining = withTraffic(activeNode(), BrokerTrafficState.DRAINING);
        DrainProgress progress = new DrainProgress(List.of(), at(0L),
                summary(0L, 0L, 0L, true));

        BrokerClusterOperationProposal change = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("drain-unfenced"))
                .commandDigest(digest("digest-unfenced"))
                .expectedRevision(new ClusterRevision(1L))
                .operationType(BrokerClusterOperationType.DRAIN_BROKER)
                .targetBrokerId(draining.id())

                .changedAt(at(1_000L))
                .initialDrainProgress(progress)
                .build();
        cluster.submitOperation(change);
        BrokerClusterDecision result = commitOperation(cluster, change);

        assertThat(result.status()).isEqualTo(BrokerClusterDecision.Status.CONFLICT);
        assertThat(result.reason().value()).contains("fence");
        assertThat(cluster.epoch()).isEqualTo(new RoutingEpoch(3L));
    }

    @Test
    void completesDrainOnlyAfterCommittedProgressIsReady() {
        BrokerCluster cluster = clusterWithTwoActiveBrokers();
        DrainProgress progress = new DrainProgress(
                List.of(checkpoint("gateway-a", 5L)), at(2_000L), null);
        BrokerNode draining = withTraffic(activeNode(), BrokerTrafficState.DRAINING);
        BrokerClusterOperationProposal change = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("drain-1"))
                .commandDigest(digest("digest-1"))
                .expectedRevision(new ClusterRevision(1L))
                .operationType(BrokerClusterOperationType.DRAIN_BROKER)
                .targetBrokerId(draining.id())

                .changedAt(at(1_000L))
                .initialDrainProgress(progress)
                .build();
        cluster.submitOperation(change);
        cluster.commitRoutingFence(fence("drain-1", 7L, 21L,
                List.of(activeNode().id(), secondActiveNode().id())));
        commitOperation(cluster, change);
        advance(cluster, "drain-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, 1_100L);
        advance(cluster, "drain-1", BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                BrokerClusterOperationPhase.REBUILDING_ROUTES, 1_200L);

        BrokerClusterDecision early = cluster.completeDrain(
                new ClusterOperationId("drain-1"), digest("digest-1"),
                new ClusterRevision(3L), at(1_900L));
        cluster.updateDrainProgress(new ClusterOperationId("drain-1"),
                digest("digest-1"), DrainProgressUpdate.confirmGateway(
                gatewayId("gateway-a"), routeRevision(8L), routeCount(2L),
                snapshotDigest("checksum-a")), at(2_000L));
        BrokerClusterDecision progressResult = cluster.updateDrainProgress(
                new ClusterOperationId("drain-1"),
                        digest("digest-1"), DrainProgressUpdate.summarize(
                        summary(2L, 2L, 0L, true)), at(2_000L));
        BrokerClusterDecision completed = cluster.completeDrain(
                new ClusterOperationId("drain-1"), digest("digest-1"),
                new ClusterRevision(3L), at(2_001L));

        assertThat(early.status()).isEqualTo(BrokerClusterDecision.Status.CONFLICT);
        assertThat(progressResult.operation().phase())
                .isEqualTo(BrokerClusterOperationPhase.VERIFYING);
        assertThat(completed.status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(completed.operation().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(4L));
        assertThat(cluster.epoch()).isEqualTo(new RoutingEpoch(4L));
        assertThat(cluster.brokers()).extracting(BrokerNode::trafficState)
                .containsExactly(BrokerTrafficState.INACTIVE, BrokerTrafficState.ACTIVE);
    }

    @Test
    void forceDrainRequiresFailedOperationAndExpectedRevision() {
        BrokerCluster cluster = clusterWithTwoActiveBrokers();
        DrainProgress progress = new DrainProgress(List.of(), at(0L),
                summary(0L, 0L, 0L, true));
        BrokerNode draining = withTraffic(activeNode(), BrokerTrafficState.DRAINING);
        BrokerClusterOperationProposal change = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("drain-1"))
                .commandDigest(digest("digest-1"))
                .expectedRevision(new ClusterRevision(1L))
                .operationType(BrokerClusterOperationType.DRAIN_BROKER)
                .targetBrokerId(draining.id())

                .changedAt(at(1_000L))
                .initialDrainProgress(progress)
                .build();
        cluster.submitOperation(change);
        cluster.commitRoutingFence(fence("drain-1", 7L, 21L,
                List.of(activeNode().id(), secondActiveNode().id())));
        commitOperation(cluster, change);
        advance(cluster, "drain-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.FAILED, 1_100L);

        BrokerClusterDecision conflict = cluster.forceDrain(
                new ClusterOperationId("drain-1"), digest("digest-1"),
                new ClusterRevision(2L), failureReason("operator approved"), at(1_200L));
        BrokerClusterDecision forced = cluster.forceDrain(
                new ClusterOperationId("drain-1"), digest("digest-1"),
                new ClusterRevision(3L), failureReason("operator approved"), at(1_200L));

        assertThat(conflict.status()).isEqualTo(BrokerClusterDecision.Status.CONFLICT);
        assertThat(forced.operation().phase()).isEqualTo(BrokerClusterOperationPhase.FORCED);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(4L));
    }

    @Test
    void fenceCanOnlyResumeWithMonotonicLeaderEvidence() {
        BrokerCluster cluster = clusterWithTwoActiveBrokers();
        BrokerNode draining = withTraffic(activeNode(), BrokerTrafficState.DRAINING);
        BrokerClusterOperationProposal change = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("operation-1"))
                .commandDigest(digest("digest-1"))
                .expectedRevision(new ClusterRevision(1L))
                .operationType(BrokerClusterOperationType.DRAIN_BROKER)
                .targetBrokerId(draining.id())

                .changedAt(at(1_000L))
                .initialDrainProgress(new DrainProgress(List.of(), at(0L),
                        summary(0L, 0L, 0L, true)))
                .build();
        cluster.submitOperation(change);
        List<BrokerId> holders = List.of(activeNode().id(), secondActiveNode().id());
        RoutingEpochFence original = fence("operation-1", 7L, 21L, holders);
        RoutingEpochFence staleTerm = fence("operation-1", 7L, 22L, holders);
        RoutingEpochFence resumed = fence("operation-1", 8L, 22L, holders);

        BrokerClusterDecision first = cluster.commitRoutingFence(original);
        BrokerClusterDecision duplicate = cluster.commitRoutingFence(original);
        BrokerClusterDecision rejected = cluster.commitRoutingFence(staleTerm);
        BrokerClusterDecision resumedResult = cluster.commitRoutingFence(resumed);

        assertThat(first.status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(duplicate.status()).isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
        assertThat(rejected.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(resumedResult.status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        assertThat(cluster.findRoutingFence()).contains(resumed);
    }

    @Test
    void rejectsFenceWithoutMatchingClusterOperation() {
        BrokerCluster cluster = cluster();

        BrokerClusterDecision result = cluster.commitRoutingFence(fence(7L, 21L));

        assertThat(result.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(result.reason().value()).contains("operation");
        assertThat(cluster.findRoutingFence()).isEmpty();
    }

    @Test
    void rejectsFenceWithIncompleteActiveBrokerSetAtCommitBoundary() {
        BrokerCluster cluster = clusterWithTwoActiveBrokers();
        BrokerClusterOperationProposal proposal = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("operation-1"))
                .commandDigest(digest("digest-1")).expectedRevision(cluster.revision())
                .operationType(BrokerClusterOperationType.DRAIN_BROKER)
                .targetBrokerId(activeNode().id()).changedAt(at(1_000L))
                .initialDrainProgress(new DrainProgress(List.of(), at(0L),
                        summary(0L, 0L, 0L, true))).build();
        assertThat(cluster.submitOperation(proposal).accepted()).isTrue();
        ClusterRevision revisionBeforeFence = cluster.revision();

        BrokerClusterDecision rejected = cluster.commitRoutingFence(
                fence("operation-1", 7L, 21L, List.of(activeNode().id())));

        assertThat(rejected.status()).isEqualTo(BrokerClusterDecision.Status.CONFLICT);
        assertThat(cluster.findRoutingFence()).isEmpty();
        assertThat(cluster.revision()).isEqualTo(revisionBeforeFence);
        RoutingEpochFence complete = fence("operation-1", 7L, 21L,
                List.of(activeNode().id(), secondActiveNode().id()));
        assertThat(cluster.commitRoutingFence(complete).accepted()).isTrue();
        assertThat(cluster.findRoutingFence()).contains(complete);
    }

    @Test
    void restoresFenceFromPreviousEpochAfterTopologyAdvances() {
        RoutingEpochFence previousFence = fence(7L, 21L);

        BrokerCluster restored = clusterBuilder()
                .revision(new ClusterRevision(2L))
                .epoch(new RoutingEpoch(4L))
                .routingFence(previousFence)
                .build();

        assertThat(restored.findRoutingFence()).contains(previousFence);
    }

    @Test
    void rejectsNullNodeWithoutLeakingNullPointerException() {
        assertThatThrownBy(() -> clusterBuilder()
                .brokers(java.util.Arrays.asList(activeNode(), null))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonTerminalHistoryCannotBeEvicted() {
        BrokerCluster cluster = clusterBuilder()
                .operationHistoryCapacity(new OperationHistoryCapacity(1))
                .build();
        BrokerNode second = BrokerNode.discovered(
                new BrokerId("broker-b"), new BrokerRouteEndpoint("10.0.0.2:18020"));
        BrokerClusterOperationProposal first = topologyChange("operation-1", new ClusterRevision(1L),
                List.of(activeNode(), second));
        cluster.submitOperation(first);
        commitOperation(cluster, first);
        BrokerNode syncing = new BrokerNode(second.id(), second.routeEndpoint(),
                BrokerMembershipRole.LEARNER_SYNCING, BrokerTrafficState.INACTIVE);

        BrokerClusterOperationProposal secondChange = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("operation-2"))
                .commandDigest(digest("digest-operation-2"))
                .expectedRevision(new ClusterRevision(3L))
                .operationType(BrokerClusterOperationType.ADD_LEARNER)
                .targetBrokerId(syncing.id())
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(at(1_000L))
                .build();
        BrokerClusterDecision result = cluster.submitOperation(secondChange);

        assertThat(result.status()).isEqualTo(BrokerClusterDecision.Status.UNAVAILABLE);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(3L));
    }

    @Test
    void valueObjectsRejectIllegalInput() {
        assertThatThrownBy(() -> new BrokerId(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ClusterRevision(-1L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RoutingEpoch(-1L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CommandDigest(" "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> clusterBuilder()
                .epoch(new RoutingEpoch(1L))
                .brokers(List.of(activeNode(), activeNode()))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrokerClusterDecision(
                BrokerClusterDecision.Status.CONFLICT, null, new DecisionReason(" ")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DrainProgressUpdate(
                DrainProgressUpdate.Kind.SUMMARIZE, null, routeRevision(1L),
                routeCount(0L), null, summary(0L, 0L, 0L, true)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BrokerFailureObservationDecision(
                BrokerFailureObservationEligibility.INELIGIBLE,
                new ObservationQuorum(1), List.of(), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BrokerCluster.builder()
                .revision(new ClusterRevision(1L))
                .epoch(new RoutingEpoch(1L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(activeNode()))
                .operations(List.of())
                .build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> clusterBuilder()
                .operationHistoryCapacity(new OperationHistoryCapacity(0))
                .build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RoutingEpochFence.builder()
                .operationId(new ClusterOperationId("operation-1"))
                .epoch(new RoutingEpoch(3L))
                .leaderTerm(term(7L))
                .quorumReadIndex(readIndex(21L))
                .fencedBrokerIds(List.of(new BrokerId("broker-a"), new BrokerId("broker-a")))
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    private BrokerCluster cluster() {
        return clusterBuilder().build();
    }

    private Instant at(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli);
    }

    private BrokerCluster clusterWithTwoActiveBrokers() {
        return clusterBuilder()
                .brokers(List.of(activeNode(), secondActiveNode()))
                .build();
    }

    private BrokerCluster clusterWithReadyBroker() {
        BrokerNode ready = new BrokerNode(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"),
                BrokerMembershipRole.VOTER, BrokerTrafficState.READY);
        return clusterBuilder().brokers(List.of(activeNode(), ready)).build();
    }

    private BrokerCluster.Builder clusterBuilder() {
        return BrokerCluster.builder()
                .revision(new ClusterRevision(1L))
                .epoch(new RoutingEpoch(3L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(activeNode()))
                .operations(List.of())
                .operationHistoryCapacity(new OperationHistoryCapacity(10));
    }

    private BrokerNode activeNode() {
        return new BrokerNode(new BrokerId("broker-a"),
                new BrokerRouteEndpoint("10.0.0.1:18020"),
                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE);
    }

    private BrokerNode secondActiveNode() {
        return new BrokerNode(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"),
                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE);
    }

    private BrokerNode withTraffic(BrokerNode node, BrokerTrafficState trafficState) {
        return new BrokerNode(node.id(), node.routeEndpoint(), node.membershipRole(), trafficState);
    }

    private BrokerClusterOperationProposal topologyChange(String operationId,
                                                ClusterRevision expectedRevision,
                                                List<BrokerNode> brokers) {
        BrokerId targetBrokerId = brokers.get(brokers.size() - 1).id();
        return BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId(operationId))
                .commandDigest(digest("digest-" + operationId))
                .expectedRevision(expectedRevision)
                .operationType(BrokerClusterOperationType.ADD_BROKER)
                .targetBrokerId(targetBrokerId)
                .routeEndpoint((brokers).stream().filter(node -> node.id().equals(targetBrokerId))
                        .findFirst().map(BrokerNode::routeEndpoint).orElse(null))
                .changedAt(at(1_000L))
                .build();
    }

    private void advance(BrokerCluster cluster,
                         String operationId,
                         BrokerClusterOperationPhase expected,
                         BrokerClusterOperationPhase next,
                         long changedAt) {
        BrokerClusterDecision result = cluster.advanceOperation(
                new ClusterOperationId(operationId), digest("digest-1"), expected, next,
                next == BrokerClusterOperationPhase.FAILED ? failureReason("failed") : null,
                at(changedAt));
        assertThat(result.status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
    }

    private OperationFailureReason failureReason(String value) {
        return new OperationFailureReason(value);
    }

    private RoutingEpochFence fence(long term, long readIndex) {
        return fence("operation-1", term, readIndex, List.of(new BrokerId("broker-a")));
    }

    private CommandDigest digest(String value) {
        return new CommandDigest(value);
    }

    private RoutingEpochFence fence(String operationId, long term, long readIndex,
                                   List<BrokerId> holders) {
        return RoutingEpochFence.builder()
                .operationId(new ClusterOperationId(operationId))
                .epoch(new RoutingEpoch(3L))
                .leaderTerm(term(term))
                .quorumReadIndex(readIndex(readIndex))
                .fencedBrokerIds(holders)
                .build();
    }
}
