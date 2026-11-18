package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitRoutingEpochFenceCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDrainOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;

import java.util.List;
import java.time.Instant;

import static com.co.kc.imchat.broker.support.BrokerDomainValues.gatewayId;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeCount;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeRevision;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.snapshotDigest;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;

/** 测试输入到集群共识命令的简洁构造器。 */
public final class BrokerClusterCommandFixture {

    private BrokerClusterCommandFixture() {
    }

    public static BrokerClusterOperationProposal topology(
            String operationId, String commandDigest, long expectedRevision,
            String operationType, String targetBrokerId, List<BrokerNode> brokers,
            long changedAtMs) {
        return topology(operationId, commandDigest, expectedRevision, operationType,
                targetBrokerId, brokers, changedAtMs, null);
    }

    public static BrokerClusterOperationProposal topology(
            String operationId, String commandDigest, long expectedRevision,
            String operationType, String targetBrokerId, List<BrokerNode> brokers,
            long changedAtMs, DrainProgress drainProgress) {
        return BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId(operationId))
                .commandDigest(new CommandDigest(commandDigest))
                .expectedRevision(new ClusterRevision(expectedRevision))
                .operationType(BrokerClusterOperationType.valueOf(operationType))
                .targetBrokerId(new BrokerId(targetBrokerId))
                .routeEndpoint(operationType.equals("ADD_BROKER")
                        ? new BrokerRouteEndpoint(brokers.stream().filter(node -> node.brokerId().equals(targetBrokerId))
                        .findFirst().orElseThrow().routeEndpoint()) : null)
                .membershipEndpoint(operationType.equals("START_LEARNER_SYNC") || operationType.equals("ADD_LEARNER")
                        ? new MembershipEndpoint(targetBrokerId + ".example:18120") : null)
                .minimumVoterCount(operationType.startsWith("DEMOTE_") || operationType.equals("REMOVE_BROKER")
                        ? new MinimumVoterCount(1) : null)
                .changedAt(Instant.ofEpochMilli(changedAtMs))
                .initialDrainProgress(drainProgress)
                .build();
    }

    public static BeginBrokerClusterOperationCommand beginOperation(
            BrokerClusterOperationProposal proposal) {
        return new BeginBrokerClusterOperationCommand(proposal);
    }

    public static CommitBrokerOperationCommand commitOperation(BrokerClusterOperationProposal proposal) {
        return new CommitBrokerOperationCommand(proposal.operationId(), proposal.commandDigest().value(),
                proposal.expectedRevision(), proposal.changedAt());
    }

    public static BrokerClusterConsensusResult applyTopologyChange(
            BrokerClusterConsensusStub consensus,
            BrokerClusterOperationProposal proposal) {
        return applyTopologyChange(consensus.asAdapter(), proposal);
    }

    public static BrokerClusterConsensusResult applyTopologyChange(
            BrokerRaftAdapter consensus,
            BrokerClusterOperationProposal proposal) {
        BrokerClusterConsensusResult begun = BrokerRaftTestBindings.submit(consensus, beginOperation(proposal))
                .toCompletableFuture().join();
        if (!begun.accepted()) {
            return begun;
        }
        if (begun.operation().phase() == BrokerClusterOperationPhase.FENCING_OLD_EPOCH) {
            RoutingEpochFence fence = RoutingEpochFence.builder()
                    .operationId(proposal.operationId())
                    .epoch(new RoutingEpoch(consensus.getCommittedState().epoch()))
                    .leaderTerm(term(1L))
                    .quorumReadIndex(readIndex(1L))
                    .fencedBrokerIds(consensus.getCommittedState().activeBrokers().stream()
                            .map(BrokerNode::brokerId).map(BrokerId::new).toList())
                    .build();
            BrokerClusterConsensusResult fenced = BrokerRaftTestBindings.submit(consensus, fenceEpoch(fence))
                    .toCompletableFuture().join();
            if (!fenced.accepted()) {
                return fenced;
            }
        }
        return BrokerRaftTestBindings.submit(consensus, commitOperation(proposal)).toCompletableFuture().join();
    }

    public static BrokerClusterConsensusCommand advanceOperation(
            String operationId, String commandDigest,
            BrokerClusterOperationPhase expectedPhase,
            BrokerClusterOperationPhase nextPhase,
            String error, long changedAtMs) {
        return AdvanceClusterOperationCommand.builder()
                .operationId(new ClusterOperationId(operationId))
                .commandDigest(commandDigest)
                .expectedPhase(expectedPhase)
                .nextPhase(nextPhase)
                .error(error)
                .changedAt(Instant.ofEpochMilli(changedAtMs))
                .build();
    }

    public static BrokerClusterConsensusCommand confirmDrainGateway(
            String operationId, String commandDigest, String gatewayId,
            long routeRevision, long routeCount, String checksum, long changedAtMs) {
        return new UpdateBrokerDrainProgressCommand(new ClusterOperationId(operationId), commandDigest,
                DrainProgressUpdate.confirmGateway(
                        gatewayId(gatewayId), routeRevision(routeRevision),
                        routeCount(routeCount), snapshotDigest(checksum)), Instant.ofEpochMilli(changedAtMs));
    }

    public static BrokerClusterConsensusCommand summarizeDrain(
            String operationId, String commandDigest, RouteRecoveryEvidence summary,
            long changedAtMs) {
        return new UpdateBrokerDrainProgressCommand(new ClusterOperationId(operationId), commandDigest,
                DrainProgressUpdate.summarize(summary), Instant.ofEpochMilli(changedAtMs));
    }

    public static BrokerClusterConsensusCommand completeDrain(
            String operationId, String commandDigest, long expectedRevision,
            long changedAtMs) {
        return new CompleteBrokerDrainCommand(new ClusterOperationId(operationId), commandDigest,
                new ClusterRevision(expectedRevision), Instant.ofEpochMilli(changedAtMs));
    }

    public static BrokerClusterConsensusCommand forceDrain(
            String operationId, String commandDigest, long expectedRevision,
            String reason, long changedAtMs) {
        return new ForceDrainOperationCommand(new ClusterOperationId(operationId), commandDigest,
                new ClusterRevision(expectedRevision), reason, Instant.ofEpochMilli(changedAtMs));
    }

    public static BrokerClusterConsensusCommand fenceEpoch(RoutingEpochFence operation) {
        return new CommitRoutingEpochFenceCommand(operation);
    }
}
