package com.co.kc.imchat.broker.transformer.application;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.model.cqrs.result.AddLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult;
import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DeactivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DrainBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.BrokerFailureRecoveryResult;
import com.co.kc.imchat.broker.model.cqrs.result.BrokerDrainResult;
import com.co.kc.imchat.broker.model.cqrs.result.ForceDeactivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.PromoteLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DemoteVoterResult;
import com.co.kc.imchat.broker.model.cqrs.result.RemoveBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.broker.model.cqrs.command.PromoteLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.RemoveBrokerCommand;

import java.time.Instant;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipEndpoint;

/**
 * Broker 集群应用结果之间的边界转换。
 */
public final class BrokerClusterAppTransformer {
    public static final BrokerClusterAppTransformer INSTANCE = new BrokerClusterAppTransformer();

    private BrokerClusterAppTransformer() {
    }

    /** 排空完成以共识接受且实际进入 SUCCEEDED 为准，提交失败保留等待及原说明。 */
    public BrokerDrainResult drainCompletionResultFrom(BrokerClusterConsensusResult result) {
        if (result.accepted() && result.operation().phase() == BrokerClusterOperationPhase.SUCCEEDED) {
            return BrokerDrainResult.succeeded();
        }
        return BrokerDrainResult.waitingForCleanup(result.message());
    }

    /** 按实际共识结果输出终态或等待状态，不在响应转换中重新选择恢复策略。 */
    public BrokerFailureRecoveryResult failureRecoveryResultFrom(
            BrokerClusterConsensusResult result, BrokerClusterOperation requestedOperation) {
        BrokerClusterOperation operation = result.operation() == null ? requestedOperation : result.operation();
        if (result.accepted()) {
            if (operation.phase() == BrokerClusterOperationPhase.SUCCEEDED) {
                return BrokerFailureRecoveryResult.succeeded();
            }
            if (operation.phase() == BrokerClusterOperationPhase.DEGRADED) {
                return BrokerFailureRecoveryResult.degraded(operation.error().value());
            }
        }
        if (operation.phase() == BrokerClusterOperationPhase.REBUILDING_ROUTES
                && !operation.drainProgress().allGatewaysConfirmed()) {
            return BrokerFailureRecoveryResult.waitingForGateways(result.message());
        }
        return BrokerFailureRecoveryResult.waitingForOldEvents(result.message());
    }

    /** 将操作身份、结果 epoch 和授权目标映射为单节点 Lease 安装命令。 */
    public ActivateRoutingLeaseCommand activateRoutingLeaseCommandFrom(
            BrokerClusterOperation operation, BrokerNode broker) {
        return new ActivateRoutingLeaseCommand(
                operation.id().value(), broker.id().value(), operation.resultEpoch().value());
    }

    public AddLearnerCommand addLearnerCommandFrom(
            BrokerClusterOperation operation, MembershipEndpoint endpoint) {
        return new AddLearnerCommand(operation.id().value(), operation.targetBrokerId().value(),
                operation.acceptedEpoch().value(), endpoint.value());
    }

    public ConfirmLearnerCommand completeLearnerSyncCommandFrom(
            BrokerClusterOperation operation, MembershipEndpoint endpoint) {
        return new ConfirmLearnerCommand(operation.id().value(), operation.targetBrokerId().value(),
                operation.acceptedEpoch().value(), endpoint.value());
    }

    public PromoteLearnerCommand promoteLearnerCommandFrom(
            BrokerClusterOperation operation, MembershipEndpoint endpoint) {
        return new PromoteLearnerCommand(operation.id().value(), operation.targetBrokerId().value(),
                operation.acceptedEpoch().value(), endpoint.value());
    }

    public DemoteVoterCommand demoteVoterCommandFrom(
            BrokerClusterOperation operation, MembershipEndpoint endpoint) {
        return new DemoteVoterCommand(operation.id().value(), operation.targetBrokerId().value(),
                operation.acceptedEpoch().value(), endpoint.value());
    }

    public RemoveBrokerCommand removeBrokerCommandFrom(
            BrokerClusterOperation operation, MembershipEndpoint endpoint) {
        return new RemoveBrokerCommand(operation.id().value(), operation.targetBrokerId().value(),
                operation.acceptedEpoch().value(), endpoint.value());
    }

    /** 共识结果映射为加入结果，不将提交状态重新解释为成功终态。 */
    public AddLearnerResult addLearnerResultFrom(
            ClusterOperationId operationId, BrokerClusterConsensusResult result) {
        return new AddLearnerResult(operationId.value(), result.status(), result.leaderEndpoint(), result.message());
    }

    /** 映射同步结果；历史操作保留接受时的 epoch，而非当前观察值。 */
    public CompleteLearnerSyncResult completeLearnerSyncResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterConsensusResult result) {
        BrokerClusterOperation operation = result.operation();
        return new CompleteLearnerSyncResult(operationId.value(), result.status(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), result.leaderEndpoint(), result.message());
    }

    /** 映射激活提交结果，保留调用方观察的接受 epoch 及共识返回的目标 epoch。 */
    public ActivateBrokerResult activateBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch acceptedEpoch, BrokerClusterConsensusResult result) {
        return activateBrokerResultFrom(operationId, acceptedEpoch, result, result.operation());
    }

    /** 使用最新操作进度构造激活响应，保留登记状态、Leader 地址和说明。 */
    public ActivateBrokerResult activateBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch acceptedEpoch,
            BrokerClusterConsensusResult result, BrokerClusterOperation operation) {
        return new ActivateBrokerResult(operationId.value(), result.status().name(),
                operation == null ? acceptedEpoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(),
                result.leaderEndpoint(), result.message());
    }

    /** 映射提交结果；历史操作使用原接受 epoch，而非当前观察值。 */
    public DeactivateBrokerResult deactivateBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterConsensusResult result) {
        return deactivateBrokerResultFrom(operationId, epoch, result, result.operation());
    }

    /** 使用最新操作进度构造停用响应，保留登记状态、Leader 地址和说明。 */
    public DeactivateBrokerResult deactivateBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch,
            BrokerClusterConsensusResult result, BrokerClusterOperation operation) {
        return new DeactivateBrokerResult(operationId.value(), result.status().name(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), result.leaderEndpoint(), result.message());
    }

    /** 映射提交结果；历史操作使用原接受 epoch，而非当前观察值。 */
    public DrainBrokerResult drainBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterConsensusResult result) {
        return drainBrokerResultFrom(operationId, epoch, result, result.operation());
    }

    /** 使用最新操作进度构造排空响应，保留登记状态、Leader 地址和说明。 */
    public DrainBrokerResult drainBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch,
            BrokerClusterConsensusResult result, BrokerClusterOperation operation) {
        return new DrainBrokerResult(operationId.value(), result.status().name(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), result.leaderEndpoint(), result.message());
    }

    /** 映射提交结果；历史操作使用原接受 epoch，而非当前观察值。 */
    public ForceDeactivateBrokerResult forceDeactivateBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterConsensusResult result) {
        return forceDeactivateBrokerResultFrom(operationId, epoch, result, result.operation());
    }

    /** 使用最新操作进度构造故障摘流响应，保留登记状态、Leader 地址和说明。 */
    public ForceDeactivateBrokerResult forceDeactivateBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch,
            BrokerClusterConsensusResult result, BrokerClusterOperation operation) {
        return new ForceDeactivateBrokerResult(operationId.value(), result.status().name(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), result.leaderEndpoint(), result.message());
    }

    /** 映射晋升结果，保留原操作接受 epoch 和目标 epoch。 */
    public PromoteLearnerResult promoteLearnerResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterConsensusResult result) {
        BrokerClusterOperation operation = result.operation();
        return new PromoteLearnerResult(operationId.value(), result.status().name(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), result.leaderEndpoint(), result.message());
    }

    /** 映射降级结果，保留原操作接受 epoch 和目标 epoch。 */
    public DemoteVoterResult demoteVoterResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterConsensusResult result) {
        BrokerClusterOperation operation = result.operation();
        return new DemoteVoterResult(operationId.value(), result.status().name(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), result.leaderEndpoint(), result.message());
    }

    /** 映射移除结果，保留原操作接受 epoch 和目标 epoch。 */
    public RemoveBrokerResult removeBrokerResultFrom(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterConsensusResult result) {
        BrokerClusterOperation operation = result.operation();
        return new RemoveBrokerResult(operationId.value(), result.status().name(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), result.leaderEndpoint(), result.message());
    }

    /**
     * 映射成员拒绝的失败命令，原样转换调用方提供的毫秒时间，不修正异常时间。
     */
    public AdvanceClusterOperationCommand membershipFailureCommandFrom(
            BrokerClusterOperation operation, BrokerConsensusMembershipResult membership,
            Instant changedAt) {
        String reason = membership.message();
        if (reason == null || reason.isBlank()) {
            reason = "membership rejected";
        }
        return AdvanceClusterOperationCommand.builder()
                .operationId(operation.id())
                .commandDigest(operation.commandDigest().value())
                .expectedPhase(BrokerClusterOperationPhase.PENDING)
                .nextPhase(BrokerClusterOperationPhase.FAILED)
                .error(reason)
                .changedAtEpochMilli(changedAt.toEpochMilli())
                .build();
    }

    /**
     * 映射已提交成员操作的成功命令，不执行阶段判断或共识提交。
     */
    public AdvanceClusterOperationCommand membershipCompletionCommandFrom(
            BrokerClusterOperation operation, Instant changedAt) {
        return AdvanceClusterOperationCommand.builder()
                .operationId(operation.id())
                .commandDigest(operation.commandDigest().value())
                .expectedPhase(BrokerClusterOperationPhase.RAFT_COMMITTED)
                .nextPhase(BrokerClusterOperationPhase.SUCCEEDED)
                .changedAtEpochMilli(changedAt.toEpochMilli())
                .build();
    }

    public BrokerClusterConsensusResult consensusResultFrom(
            BrokerConsensusMembershipResult source) {
        BrokerClusterConsensusStatus status = switch (source.status()) {
            case NOT_LEADER -> BrokerClusterConsensusStatus.NOT_LEADER;
            case REJECTED -> BrokerClusterConsensusStatus.REJECTED;
            case UNAVAILABLE -> BrokerClusterConsensusStatus.UNAVAILABLE;
            case APPLIED, DUPLICATE -> throw new IllegalArgumentException(
                    "accepted membership result must continue state submission");
        };
        return new BrokerClusterConsensusResult(status, null, source.leaderEndpoint(), source.message());
    }

    /**
     * 将未提交的领域拒绝转换为应用层共识结果。
     */
    public BrokerClusterConsensusResult consensusResultFrom(
            BrokerClusterDecision source) {
        return new BrokerClusterConsensusResult(BrokerClusterConsensusStatus.valueOf(source.status().name()), source.operation(), null, source.reason() == null ? null : source.reason().value());
    }
}
