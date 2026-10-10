package com.co.kc.imchat.broker.adapter.cluster.raft;

import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
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
import com.co.kc.imchat.broker.transformer.domain.BrokerClusterDomainTransformer;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerClusterRaftTransformer;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * JRaft 回调使用的同步状态机适配器。
 *
 * <p>集群不变量与状态转换全部由 {@link BrokerCluster} 处理；本类只负责共识命令重放、
 * SDK 投影和 snapshot 生命周期。同步边界同时协调本机 Lease 签发与日志应用，
 * 防止签发资格检查和序列分配之间插入 fence 或拓扑变更。</p>
 */
public final class RaftBrokerClusterStateMachine {

    private final int operationHistoryCapacity;
    private final String clusterId;
    private BrokerCluster cluster;

    public RaftBrokerClusterStateMachine(String clusterId, BrokerClusterState initialState) {
        this(clusterId, initialState, 1000);
    }

    public RaftBrokerClusterStateMachine(String clusterId, BrokerClusterState initialState,
                                         int operationHistoryCapacity) {
        this(clusterId, initialState, operationHistoryCapacity, java.util.Map.of());
    }

    public RaftBrokerClusterStateMachine(String clusterId, BrokerClusterState initialState,
                                         int operationHistoryCapacity,
                                         java.util.Map<String, String> initialMembershipEndpoints) {
        if (clusterId == null || clusterId.isBlank()) {
            throw new IllegalArgumentException("clusterId must not be blank");
        }
        if (operationHistoryCapacity < 1) {
            throw new IllegalArgumentException("operationHistoryCapacity must be positive");
        }
        this.clusterId = clusterId;
        this.operationHistoryCapacity = operationHistoryCapacity;
        this.cluster = BrokerClusterDomainTransformer.INSTANCE.restoreAggregate(initialState, List.of(), null,
                operationHistoryCapacity, initialMembershipEndpoints);
    }

    public synchronized BrokerClusterConsensusResult apply(BrokerClusterConsensusCommand command) {
        BrokerClusterDecision result = apply(cluster, command);
        return new BrokerClusterConsensusResult(BrokerClusterConsensusStatus.valueOf(result.status().name()), result.operation(), null, result.reason() == null ? null : result.reason().value());
    }

    private BrokerClusterDecision apply(BrokerCluster cluster,
                                        BrokerClusterConsensusCommand command) {
        if (command instanceof BeginBrokerClusterOperationCommand begin) {
            return cluster.submitOperation(begin.proposal());
        }
        if (command instanceof CommitBrokerOperationCommand commit) {
            return cluster.commitOperation(commit.operationId(),
                    new CommandDigest(commit.commandDigest()), commit.expectedRevision(),
                    Instant.ofEpochMilli(commit.changedAtEpochMilli()));
        }
        if (command instanceof AdvanceClusterOperationCommand advance) {
            return cluster.advanceOperation(advance.operationId(),
                    new CommandDigest(advance.commandDigest()),
                    advance.expectedPhase(), advance.nextPhase(),
                    failureReason(advance.error()),
                    Instant.ofEpochMilli(advance.changedAtEpochMilli()));
        }
        if (command instanceof CommitRoutingEpochFenceCommand fence) {
            return cluster.commitRoutingFence(fence.operation());
        }
        if (command instanceof UpdateBrokerDrainProgressCommand progress) {
            return cluster.updateDrainProgress(progress.operationId(),
                    new CommandDigest(progress.commandDigest()),
                    progress.progressUpdate(),
                    Instant.ofEpochMilli(progress.changedAtEpochMilli()));
        }
        if (command instanceof CompleteBrokerDrainCommand complete) {
            return cluster.completeDrain(complete.operationId(),
                    new CommandDigest(complete.commandDigest()),
                    complete.expectedRevision(),
                    Instant.ofEpochMilli(complete.changedAtEpochMilli()));
        }
        if (command instanceof ForceDrainOperationCommand force) {
            return cluster.forceDrain(force.operationId(),
                    new CommandDigest(force.commandDigest()),
                    force.expectedRevision(), failureReason(force.reason()),
                    Instant.ofEpochMilli(force.changedAtEpochMilli()));
        }
        if (command instanceof VerifyRouteRecoveryCommand verify) {
            return cluster.verifyRouteRecovery(verify.operationId(), verify.commandDigest(),
                    verify.recoveryTimeout(), verify.changedAt());
        }
        if (command instanceof CompleteRouteRecoveryCommand complete) {
            return cluster.completeRouteRecovery(complete.operationId(), complete.commandDigest(), complete.changedAt());
        }
        throw new IllegalArgumentException("unsupported Broker cluster consensus command");
    }

    private OperationFailureReason failureReason(String value) {
        return value == null ? null : new OperationFailureReason(value);
    }


    public synchronized BrokerClusterState committedState() {
        return BrokerClusterDomainTransformer.INSTANCE.toSdkState(cluster);
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

    public synchronized BrokerClusterSnapshot snapshot() {
        return new BrokerClusterSnapshot(BrokerClusterSnapshot.CURRENT_FORMAT_VERSION, clusterId,
                BrokerClusterDomainTransformer.INSTANCE.toSdkState(cluster), cluster.operations(),
                cluster.findRoutingFence().orElse(null), cluster.membershipEndpoints().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().value(),
                        entry -> entry.getValue().value())));
    }

    public synchronized void restore(BrokerClusterSnapshot snapshot) {
        if (!clusterId.equals(snapshot.clusterId())) {
            throw new IllegalArgumentException("snapshot clusterId does not match local cluster");
        }
        cluster = BrokerClusterDomainTransformer.INSTANCE.restoreAggregate(snapshot.state(), snapshot.operations(),
                snapshot.fencingOperation(), operationHistoryCapacity, snapshot.membershipEndpoints());
    }

    /** 查询本机状态机已应用的路由隔离事实；尚未登记时返回空。 */
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
