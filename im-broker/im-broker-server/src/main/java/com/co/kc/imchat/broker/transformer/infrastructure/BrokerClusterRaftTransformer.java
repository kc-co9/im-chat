package com.co.kc.imchat.broker.transformer.infrastructure;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal;
import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDrainOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.model.cqrs.command.VerifyRouteRecoveryCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteRouteRecoveryCommand;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.plugin.raft.model.RaftResult;

import java.time.Instant;
import java.time.Duration;

/**
 * 领域操作与 Raft 日志载荷之间的转换，不执行领域判断或共识提交。
 */
public final class BrokerClusterRaftTransformer {
    public static final BrokerClusterRaftTransformer INSTANCE = new BrokerClusterRaftTransformer();

    private BrokerClusterRaftTransformer() {
    }

    /** 将领域确认事实映射为日志载荷，不在转换器中判断确认是否可以接受。 */
    public UpdateBrokerDrainProgressCommand gatewayConfirmationCommandFrom(
            BrokerClusterOperation operation, DrainProgressUpdate confirmation, Instant changedAt) {
        return new UpdateBrokerDrainProgressCommand(operation.id(), operation.commandDigest().value(),
                confirmation, changedAt.toEpochMilli());
    }

    /** 映射路由重建和清理摘要，不在转换器中判断是否可以完成操作。 */
    public UpdateBrokerDrainProgressCommand recoveryEvidenceCommandFrom(
            BrokerClusterOperation operation, RouteRecoveryEvidence evidence, Instant changedAt) {
        return new UpdateBrokerDrainProgressCommand(operation.id(), operation.commandDigest().value(),
                DrainProgressUpdate.summarize(evidence), changedAt.toEpochMilli());
    }

    /** 验证命令只传递操作身份与确定的时间策略，资格及降级原因由聚合计算。 */
    public VerifyRouteRecoveryCommand recoveryVerificationCommandFrom(
            BrokerClusterOperation operation, Duration recoveryTimeout, Instant changedAt) {
        return new VerifyRouteRecoveryCommand(operation.id(), operation.commandDigest(), recoveryTimeout, changedAt);
    }

    /** 完成命令不指定正常或降级终态，避免覆盖当前已持久化意图。 */
    public CompleteRouteRecoveryCommand recoveryCompletionCommandFrom(BrokerClusterOperation operation, Instant changedAt) {
        return new CompleteRouteRecoveryCommand(operation.id(), operation.commandDigest(), changedAt);
    }

    /** 排空完成提交携带最新观察到的集群 revision，由日志应用边界重验。 */
    public CompleteBrokerDrainCommand drainCompletionCommandFrom(
            BrokerClusterOperation operation, ClusterRevision revision, Instant changedAt) {
        return new CompleteBrokerDrainCommand(operation.id(), operation.commandDigest().value(),
                revision, changedAt.toEpochMilli());
    }

    /** 映射显式接受残留风险的强制排空请求。 */
    public ForceDrainOperationCommand forcedDrainCommandFrom(
            BrokerClusterOperation operation, ClusterRevision revision, OperationFailureReason reason, Instant changedAt) {
        return new ForceDrainOperationCommand(operation.id(), operation.commandDigest().value(),
                revision, reason.value(), changedAt.toEpochMilli());
    }

    /** 将业务阶段提交转换为日志载荷，阶段选择由调用方的具名行为决定。 */
    public AdvanceClusterOperationCommand operationPhaseCommandFrom(
            BrokerClusterOperation operation, BrokerClusterOperationPhase expectedPhase,
            BrokerClusterOperationPhase nextPhase, OperationFailureReason reason, Instant changedAt) {
        return AdvanceClusterOperationCommand.builder()
                .operationId(operation.id()).commandDigest(operation.commandDigest().value())
                .expectedPhase(expectedPhase).nextPhase(nextPhase)
                .error(reason == null ? null : reason.value()).changedAtEpochMilli(changedAt.toEpochMilli()).build();
    }

    /** 将排空失败事实映射为阶段变更载荷，预期阶段使用调用方观察到的操作阶段。 */
    public AdvanceClusterOperationCommand drainFailureCommandFrom(
            BrokerClusterOperation operation, OperationFailureReason reason, Instant changedAt) {
        return AdvanceClusterOperationCommand.builder()
                .operationId(operation.id())
                .commandDigest(operation.commandDigest().value())
                .expectedPhase(operation.phase())
                .nextPhase(BrokerClusterOperationPhase.FAILED)
                .error(reason.value())
                .changedAtEpochMilli(changedAt.toEpochMilli())
                .build();
    }

    /** Raft 未应用命令时映射技术失败，不伪造关联 operation。 */
    public BrokerClusterConsensusResult consensusFailureResultFrom(
            RaftResult<BrokerClusterConsensusResult> source) {
        BrokerClusterConsensusStatus status = source.status() == RaftResult.Status.NOT_LEADER
                ? BrokerClusterConsensusStatus.NOT_LEADER : BrokerClusterConsensusStatus.UNAVAILABLE;
        return new BrokerClusterConsensusResult(status, null, source.leaderEndpoint(), source.message());
    }

    /** 转换 Raft 成员变更返回值，保留处理状态、Leader 端点和原因，不附带配置快照。 */
    public BrokerConsensusMembershipResult membershipResultFrom(
            RaftResult<Void> source) {
        BrokerConsensusMembershipResult.Status status = switch (source.status()) {
            case APPLIED -> BrokerConsensusMembershipResult.Status.APPLIED;
            case NOT_LEADER -> BrokerConsensusMembershipResult.Status.NOT_LEADER;
            case UNAVAILABLE -> BrokerConsensusMembershipResult.Status.UNAVAILABLE;
        };
        return new BrokerConsensusMembershipResult(status, source.leaderEndpoint(), source.message());
    }

    /** 生成只引用 Begin 已登记操作的 Commit 命令。 */
    public CommitBrokerOperationCommand commitCommandFrom(
            BrokerClusterOperation operation, Instant changedAt) {
        return new CommitBrokerOperationCommand(
                operation.id(),
                operation.commandDigest().value(),
                operation.acceptedRevision(),
                changedAt.toEpochMilli());
    }

    public BrokerClusterOperationProposal proposalFrom(BrokerClusterOperation operation) {
        return BrokerClusterOperationProposal.builder()
                .operationId(operation.id()).commandDigest(operation.commandDigest())
                .expectedRevision(operation.acceptedRevision()).operationType(operation.type())
                .targetBrokerId(operation.targetBrokerId())
                .changedAt(operation.createdAt()).initialDrainProgress(operation.drainProgress())
                .membershipEndpoint(operation.membershipEndpoint())
                .routeEndpoint(operation.routeEndpoint()).minimumVoterCount(operation.minimumVoterCount())
                .build();
    }
}
