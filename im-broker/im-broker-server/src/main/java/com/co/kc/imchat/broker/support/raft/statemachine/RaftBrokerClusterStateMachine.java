package com.co.kc.imchat.broker.support.raft.statemachine;

import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitRoutingEpochFenceCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDrainOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.model.cqrs.command.VerifyRouteRecoveryCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteRouteRecoveryCommand;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JRaft 回调使用的同步状态机适配器。
 *
 * <p>集群不变量与状态转换全部由 {@link BrokerCluster} 处理；本类只负责共识命令重放、
 * SDK 投影和 snapshot 生命周期。同步边界同时协调本机 Lease 签发与日志应用，
 * 防止签发资格检查和序列分配之间插入 fence 或拓扑变更。</p>
 */
public final class RaftBrokerClusterStateMachine implements RaftStateHandler<BrokerClusterConsensusCommand, BrokerClusterConsensusResult> {

    private final int operationHistoryCapacity;
    private final String clusterId;
    private BrokerCluster cluster;

    public RaftBrokerClusterStateMachine(
            String clusterId,
            BrokerClusterState clusterState,
            int operationHistoryCapacity,
            Map<String, String> initialMembershipEndpoints) {
        AssertUtils.argNotBlank("clusterId must not be blank", clusterId);
        AssertUtils.argTrue("operationHistoryCapacity must be positive", operationHistoryCapacity >= 1);
        this.clusterId = clusterId;
        this.operationHistoryCapacity = operationHistoryCapacity;
        this.cluster = BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(clusterState, List.of(), null,
                operationHistoryCapacity, initialMembershipEndpoints);
    }

    /**
     * 在同一同步边界内生成完整快照并编码，避免状态在编码期间变化。
     */
    @Override
    public synchronized byte[] snapshot() {
        return BrokerClusterSnapshot.encode(snapshotState());
    }

    /**
     * 解码失败不替换现有聚合；身份与原子恢复仍由 typed restore 校验。
     */
    @Override
    public void restore(byte[] snapshot) {
        restore(BrokerClusterSnapshot.decode(snapshot));
    }

    @Override
    public synchronized BrokerClusterConsensusResult apply(BrokerClusterConsensusCommand command) {
        BrokerClusterDecision result = switch (command) {
            case BeginBrokerClusterOperationCommand begin -> cluster.submitOperation(begin.proposal());
            case CommitBrokerOperationCommand commit -> cluster.commitOperation(
                    commit.operationId(), new CommandDigest(commit.commandDigest()), commit.expectedRevision(),
                    commit.changedAt());
            case AdvanceClusterOperationCommand advance -> cluster.advanceOperation(
                    advance.operationId(), new CommandDigest(advance.commandDigest()),
                    advance.expectedPhase(), advance.nextPhase(), failureReason(advance.error()),
                    advance.changedAt());
            case CommitRoutingEpochFenceCommand fence -> cluster.commitRoutingFence(fence.operation());
            case UpdateBrokerDrainProgressCommand progress -> cluster.updateDrainProgress(
                    progress.operationId(), new CommandDigest(progress.commandDigest()), progress.progressUpdate(),
                    progress.changedAt());
            case CompleteBrokerDrainCommand complete -> cluster.completeDrain(
                    complete.operationId(), new CommandDigest(complete.commandDigest()), complete.expectedRevision(),
                    complete.changedAt());
            case ForceDrainOperationCommand force -> cluster.forceDrain(
                    force.operationId(), new CommandDigest(force.commandDigest()),
                    force.expectedRevision(), failureReason(force.reason()),
                    force.changedAt());
            case VerifyRouteRecoveryCommand verify -> cluster.verifyRouteRecovery(
                    verify.operationId(), verify.commandDigest(), verify.recoveryTimeout(), verify.changedAt());
            case CompleteRouteRecoveryCommand complete -> cluster.completeRouteRecovery(
                    complete.operationId(), complete.commandDigest(), complete.changedAt());
            case null -> throw new IllegalArgumentException("unsupported Broker cluster consensus command");
        };
        return new BrokerClusterConsensusResult(
                Status.valueOf(result.status().name()), result.operation(), null,
                result.reason() == null ? null : result.reason().value());
    }

    private OperationFailureReason failureReason(String value) {
        return value == null ? null : new OperationFailureReason(value);
    }


    public synchronized BrokerClusterState committedState() {
        return BrokerClusterStateTransformer.INSTANCE.brokerClusterStateFrom(cluster);
    }

    public synchronized BrokerCluster getCluster() {
        return cluster.snapshot();
    }

    /**
     * 与日志应用及快照恢复共用同步边界，原子执行当前资格判断和本机租约签发。
     * fence 先应用则拒绝签发；签发先执行则属于关闭前已签发的 Lease，由隔离协议处理。
     * 此处不执行 RPC 或等待 quorum，也不把本机签发序列写入 Raft 日志。
     */
    public synchronized LeaseGrantResult grantLease(
            LeaseGrantRequest request, QuorumRead quorumRead, RoutingLeaseGrantService grantService) {
        return cluster.checkLeaseGrant(request.brokerId(), request.routingEpoch())
                .orElseGet(() -> grantService.grant(request, quorumRead));
    }

    public synchronized Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
        return cluster.findOperation(operationId);
    }

    public synchronized Optional<BrokerClusterOperation> findActiveDrainOperation(
            RoutingEpoch targetEpoch) {
        return findSingleActiveRouteOperation(targetEpoch, true);
    }

    public synchronized Optional<BrokerClusterOperation> findActiveRouteRecoveryOperation(
            RoutingEpoch targetEpoch) {
        return findSingleActiveRouteOperation(targetEpoch, false);
    }

    public synchronized BrokerClusterSnapshot snapshotState() {
        return new BrokerClusterSnapshot(BrokerClusterSnapshot.CURRENT_FORMAT_VERSION, clusterId,
                BrokerClusterStateTransformer.INSTANCE.brokerClusterStateFrom(cluster), cluster.operations(),
                cluster.findRoutingFence().orElse(null), cluster.membershipEndpoints().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().value(),
                        entry -> entry.getValue().value())));
    }

    public synchronized void restore(BrokerClusterSnapshot snapshot) {
        if (!clusterId.equals(snapshot.clusterId())) {
            throw new IllegalArgumentException("snapshot clusterId does not match local cluster");
        }
        cluster = BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(snapshot.state(), snapshot.operations(),
                snapshot.fencingOperation(), operationHistoryCapacity, snapshot.membershipEndpoints());
    }

    /**
     * 查询本机状态机已应用的路由隔离事实；尚未登记时返回空。
     */
    public synchronized Optional<RoutingEpochFence> findRoutingFence() {
        return cluster.findRoutingFence();
    }

    private Optional<BrokerClusterOperation> findSingleActiveRouteOperation(
            RoutingEpoch targetEpoch, boolean drainOnly) {
        List<BrokerClusterOperation> matches = cluster.operations().stream()
                .filter(operation -> operation.resultEpoch().equals(targetEpoch))
                .filter(operation -> !operation.phase().terminal())
                .filter(operation -> drainOnly
                        ? operation.type() == BrokerClusterOperationType.DRAIN_BROKER
                        : operation.type().routeRecovery())
                .toList();
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "multiple active route recovery operations for epoch " + targetEpoch);
        }
        return matches.stream().findFirst();
    }
}
