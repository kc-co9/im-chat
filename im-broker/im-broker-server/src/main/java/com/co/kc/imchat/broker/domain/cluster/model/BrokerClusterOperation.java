package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Broker 集群操作的生命周期实体。
 *
 * <p>实体以 {@link ClusterOperationId} 标识一次集群变更，并冻结其请求摘要、接收时的
 * revision/epoch、当前阶段和恢复事实，不持有目标拓扑。状态只能沿允许的阶段顺序推进；失败、
 * 降级或强制结束时保留原因，终态操作记录完成时间。</p>
 *
 * <p>该对象由 {@link BrokerCluster} 保存和推进，覆盖拓扑提交前后的 fence、授权新纪元、
 * 路由重建、证据核对和人工强制收口等场景。它只表达领域生命周期，不负责 Raft 提交、
 * RPC 调用或持久化。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@EqualsAndHashCode
@ToString
@Getter
@Accessors(fluent = true)
public final class BrokerClusterOperation implements Serializable {
    /* 集群操作业务标识。 */
    private ClusterOperationId id;
    /* 操作命令摘要，用于幂等和重复命令识别。 */
    private CommandDigest commandDigest;
    /* 集群操作类型。 */
    private BrokerClusterOperationType type;
    /* 操作目标 Broker。 */
    private BrokerId targetBrokerId;
    /* 操作被接收时的当前路由纪元。 */
    private RoutingEpoch acceptedEpoch;
    /* 操作被接收时的集群修订号。 */
    private ClusterRevision acceptedRevision;
    /* 本次操作应用后的路由纪元；不改变路由集合时与 acceptedEpoch 相同，不代表已提交生效。 */
    private RoutingEpoch resultEpoch;
    /* 操作当前阶段。 */
    private BrokerClusterOperationPhase phase;
    /* 失败或降级完成时的领域原因。 */
    private OperationFailureReason error;
    /* 操作创建时间。 */
    private Instant createdAt;
    /* 操作进入终态的时间。 */
    private Instant completedAt;
    /* 路由恢复操作的 drain 进度。 */
    private DrainProgress drainProgress;
    /* 首次加入 learner 时绑定的成员通信端点。 */
    private MembershipEndpoint membershipEndpoint;
    /* 新增 Broker 时冻结的路由端点。 */
    private BrokerRouteEndpoint routeEndpoint;
    /* 降级或移除时冻结的最低 voter 数量。 */
    private MinimumVoterCount minimumVoterCount;

    private BrokerClusterOperation() {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private BrokerClusterOperation(ClusterOperationId id,
                                   CommandDigest commandDigest,
                                   BrokerClusterOperationType type,
                                   BrokerId targetBrokerId,
                                   RoutingEpoch acceptedEpoch,
                                   ClusterRevision acceptedRevision,
                                   RoutingEpoch resultEpoch,
                                   BrokerClusterOperationPhase phase,
                                   OperationFailureReason error,
                                   Instant createdAt,
                                   Instant completedAt,
                                   DrainProgress drainProgress,
                                   MembershipEndpoint membershipEndpoint,
                                   BrokerRouteEndpoint routeEndpoint,
                                   MinimumVoterCount minimumVoterCount) {
        this.id = id;
        this.commandDigest = commandDigest;
        this.type = type;
        this.targetBrokerId = targetBrokerId;
        this.acceptedEpoch = acceptedEpoch;
        this.acceptedRevision = acceptedRevision;
        this.resultEpoch = resultEpoch;
        this.phase = phase;
        this.error = error;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.drainProgress = drainProgress;
        this.membershipEndpoint = membershipEndpoint;
        this.routeEndpoint = routeEndpoint;
        this.minimumVoterCount = minimumVoterCount;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 开始操作生命周期，冻结登记事实并确定是否先进入旧纪元隔离阶段，不修改集群。 */
    public static BrokerClusterOperation begin(BrokerClusterOperationProposal proposal,
                                               ClusterRevision revision, RoutingEpoch epoch) {
        RoutingEpoch resultEpoch = proposal.changesRouting() ? epoch.next() : epoch;
        BrokerClusterOperationPhase initialPhase = resultEpoch.equals(epoch)
                ? BrokerClusterOperationPhase.PENDING
                : BrokerClusterOperationPhase.FENCING_OLD_EPOCH;
        return builder()
                .id(proposal.operationId())
                .commandDigest(proposal.commandDigest())
                .type(proposal.operationType())
                .targetBrokerId(proposal.targetBrokerId())
                .acceptedEpoch(epoch)
                .acceptedRevision(revision)
                .resultEpoch(resultEpoch)
                .phase(initialPhase)
                .createdAt(proposal.changedAt())
                .drainProgress(proposal.initialDrainProgress())
                .membershipEndpoint(proposal.membershipEndpoint())
                .routeEndpoint(proposal.routeEndpoint())
                .minimumVoterCount(proposal.minimumVoterCount())
                .build();
    }

    /** 本操作是否属于首次加入 learner，登记时需要保存成员端点绑定。 */
    public boolean learnerJoin() {
        return type == BrokerClusterOperationType.START_LEARNER_SYNC
                || type == BrokerClusterOperationType.ADD_LEARNER;
    }

    /**
     * 该操作类型是否允许依据明确成员拒绝记录 FAILED。
     * 晋升、降级可能部分完成，拒绝时应保留操作等待恢复；拒绝是否明确由应用层判断。
     */
    public boolean canFailOnRejection() {
        return switch (type) {
            case ADD_LEARNER, START_LEARNER_SYNC, REMOVE_BROKER -> true;
            default -> false;
        };
    }

    /**
     * 当前操作是否仍独占集群变更。路由恢复必须到终态，普通成员操作提交后即可释放。
     */
    public boolean blocksNewOperation() {
        return switch (phase) {
            case PENDING, FENCING_OLD_EPOCH, AUTHORIZING_NEW_EPOCH -> true;
            case RAFT_COMMITTED -> type.routeRecovery() || !acceptedEpoch.equals(resultEpoch);
            case REBUILDING_ROUTES, VERIFYING -> type.routeRecovery();
            case SUCCEEDED, DEGRADED, FAILED, FORCED -> false;
        };
    }

    /**
     * 判断已登记操作能否复用本次意图，不修改操作阶段或重新执行节点状态转换。
     *
     * <p>revision 和时间不参与重复身份匹配；新请求才检查 revision，已登记请求沿用冻结事实。</p>
     */
    public BrokerClusterDecision checkRepeatedIntent(BrokerChangeIntent intent) {
        if (!commandDigest.equals(intent.commandDigest())
                || type != intent.operationType()
                || !targetBrokerId.equals(intent.brokerId())) {
            return BrokerClusterDecision.rejected(this,
                    new DecisionReason("operationId is already bound to another request"));
        }
        if (phase == BrokerClusterOperationPhase.FAILED) {
            return BrokerClusterDecision.rejected(this, new DecisionReason("operation has failed"));
        }
        return BrokerClusterDecision.duplicate(this, new DecisionReason("operation was already registered"));
    }

    /** 只有排空操作已进入路由重建或后续确认阶段时，才允许旧节点执行只删除的清理。 */
    public boolean canCleanupRoutes() {
        if (type != BrokerClusterOperationType.DRAIN_BROKER) {
            return false;
        }
        return phase == BrokerClusterOperationPhase.REBUILDING_ROUTES
                || phase == BrokerClusterOperationPhase.VERIFYING
                || phase == BrokerClusterOperationPhase.SUCCEEDED
                || phase == BrokerClusterOperationPhase.DEGRADED
                || phase == BrokerClusterOperationPhase.FORCED;
    }

    /** 核对本机清理请求与已登记操作的目标、代次、事件期限和完整 Gateway 确认是否一致。 */
    public boolean matchesRouteCleanup(BrokerId localBrokerId, RoutingEpoch oldEpoch, RoutingEpoch newEpoch,
                                        Instant oldEventDeadline, List<DrainProgressUpdate> confirmations) {
        return canCleanupRoutes()
                && targetBrokerId.equals(localBrokerId)
                && acceptedEpoch.equals(oldEpoch) && resultEpoch.equals(newEpoch)
                // RPC 期限使用毫秒精度，持久化事实可能含更高精度。
                && drainProgress.oldEventDeadline().toEpochMilli() == oldEventDeadline.toEpochMilli()
                && drainProgress.matchesGatewayConfirmations(confirmations);
    }

    public BrokerClusterOperation advance(BrokerClusterOperationPhase next,
                                          OperationFailureReason nextError,
                                          Instant changedAt) {
        if (!canAdvanceTo(next)) {
            throw new IllegalArgumentException("operation phase cannot advance");
        }
        // 故障恢复的 VERIFYING 也可能承载降级意图，不能仅凭阶段合法就提交正常成功。
        if (type == BrokerClusterOperationType.FAIL_BROKER && next == BrokerClusterOperationPhase.SUCCEEDED
                && (error != null || !drainProgress.readyToComplete(changedAt))) {
            throw new IllegalStateException("failure recovery evidence does not allow successful completion");
        }
        return copyBuilder().phase(next).error(nextError)
                .completedAt(next.terminal() ? changedAt : null)
                .build();
    }

    public BrokerClusterOperation updateDrainProgress(DrainProgress nextProgress) {
        if (drainProgress == null || nextProgress == null) {
            throw new IllegalArgumentException("drain progress is required");
        }
        return copyBuilder().drainProgress(nextProgress).build();
    }

    /** 排空或故障恢复仍在重建阶段且 ACK 未齐、操作期限未到时，继续等待 Gateway。 */
    public boolean waitingForGatewayAcks(Instant now, Duration recoveryTimeout) {
        return type.routeRecovery()
                && phase == BrokerClusterOperationPhase.REBUILDING_ROUTES
                && !drainProgress.allGatewaysConfirmed()
                && !timedOut(now, recoveryTimeout);
    }

    /** 操作等待期限从已登记创建时间起算，到达截止时刻即为超时；不读取本机配置或时间。 */
    public boolean timedOut(Instant now, Duration operationTimeout) {
        return !now.isBefore(createdAt.plus(operationTimeout));
    }

    /** 优雅排空在 ACK 齐全且尚未持久化清理证据时，才需要请求旧节点清理。 */
    public boolean needsRouteCleanup() {
        return type == BrokerClusterOperationType.DRAIN_BROKER
                && phase == BrokerClusterOperationPhase.REBUILDING_ROUTES
                && drainProgress.allGatewaysConfirmed()
                && drainProgress.recoveryEvidence() == null;
    }

    /** 核验已保存证据并登记完成意图；已验证操作复用原意图，缺失 ACK 只有超时后才允许降级。 */
    public BrokerClusterOperation verifyRouteRecovery(Duration recoveryTimeout, Instant changedAt) {
        if (type != BrokerClusterOperationType.FAIL_BROKER) {
            throw new IllegalArgumentException("operation is not FAIL_BROKER");
        }
        if (phase == BrokerClusterOperationPhase.VERIFYING) {
            return this;
        }
        if (phase != BrokerClusterOperationPhase.REBUILDING_ROUTES) {
            throw new IllegalStateException("route recovery is not rebuilding routes");
        }
        if (recoveryTimeout.isNegative() || recoveryTimeout.isZero()) {
            throw new IllegalArgumentException("recovery timeout must be positive");
        }
        if (drainProgress.recoveryEvidence() == null) {
            throw new IllegalStateException("route recovery evidence is required");
        }
        if (drainProgress.allGatewaysConfirmed()) {
            if (!drainProgress.readyToComplete(changedAt)) {
                throw new IllegalStateException("route recovery evidence is not ready to complete");
            }
            return advance(BrokerClusterOperationPhase.VERIFYING, null, changedAt);
        }
        if (waitingForGatewayAcks(changedAt, recoveryTimeout)) {
            throw new IllegalStateException("Gateway snapshot ACK timeout has not elapsed");
        }
        OperationFailureReason reason = new OperationFailureReason("missing Gateway snapshot ACKs: "
                + drainProgress.missingGatewayIds().stream().map(GatewayId::value).toList());
        return advance(BrokerClusterOperationPhase.VERIFYING, reason, changedAt);
    }

    /** 根据已持久化的验证意图完成故障恢复；正常完成仍由 advance 核对完整证据。 */
    public BrokerClusterOperation completeRouteRecovery(Instant changedAt) {
        if (type != BrokerClusterOperationType.FAIL_BROKER) {
            throw new IllegalArgumentException("operation is not FAIL_BROKER");
        }
        if (phase == BrokerClusterOperationPhase.SUCCEEDED || phase == BrokerClusterOperationPhase.DEGRADED) {
            return this;
        }
        if (phase != BrokerClusterOperationPhase.VERIFYING || drainProgress.recoveryEvidence() == null) {
            throw new IllegalStateException("route recovery is not verified");
        }
        BrokerClusterOperationPhase completion = error == null
                ? BrokerClusterOperationPhase.SUCCEEDED : BrokerClusterOperationPhase.DEGRADED;
        return advance(completion, error, changedAt);
    }

    private Builder copyBuilder() {
        return builder().id(id).commandDigest(commandDigest).type(type)
                .targetBrokerId(targetBrokerId).acceptedEpoch(acceptedEpoch)
                .acceptedRevision(acceptedRevision).resultEpoch(resultEpoch).phase(phase)
                .error(error).createdAt(createdAt)
                .completedAt(completedAt)
                .drainProgress(drainProgress).membershipEndpoint(membershipEndpoint)
                .routeEndpoint(routeEndpoint).minimumVoterCount(minimumVoterCount);
    }

    private boolean canAdvanceTo(BrokerClusterOperationPhase next) {
        if (next == null) { return false; }
        if (phase == BrokerClusterOperationPhase.FAILED
                && next == BrokerClusterOperationPhase.FORCED) {
            return true;
        }
        if (phase.terminal()) { return false; }
        if (next == BrokerClusterOperationPhase.FAILED) { return true; }
        if (phase == BrokerClusterOperationPhase.VERIFYING
                && next == BrokerClusterOperationPhase.DEGRADED) {
            return true;
        }
        return switch (phase) {
            case PENDING, FENCING_OLD_EPOCH -> next == BrokerClusterOperationPhase.RAFT_COMMITTED;
            case RAFT_COMMITTED -> next == BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH
                    || (next == BrokerClusterOperationPhase.SUCCEEDED
                    && acceptedEpoch.equals(resultEpoch) && !type.routeRecovery());
            case AUTHORIZING_NEW_EPOCH -> next == BrokerClusterOperationPhase.REBUILDING_ROUTES;
            case REBUILDING_ROUTES -> next == BrokerClusterOperationPhase.VERIFYING;
            case VERIFYING -> next == BrokerClusterOperationPhase.SUCCEEDED;
            case SUCCEEDED, DEGRADED, FAILED, FORCED -> false;
        };
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull("cluster operation is invalid", id, commandDigest,
                type, targetBrokerId, acceptedEpoch, acceptedRevision, resultEpoch, phase);
        AssertUtils.domainPropNotNull("cluster operation creation time is required", createdAt);
        // operation 的共识时间字段采用毫秒精度，构建与 JSON 重建保持一致。
        createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
        if (completedAt != null) {
            completedAt = completedAt.truncatedTo(ChronoUnit.MILLIS);
        }
        AssertUtils.domainPropTrue("cluster operation target epoch cannot move backwards",
                resultEpoch.value() >= acceptedEpoch.value());
        AssertUtils.domainPropTrue("operation completion precedes creation",
                completedAt == null || !completedAt.isBefore(createdAt));
        AssertUtils.domainPropTrue("route recovery operation requires drain progress",
                type.routeRecovery() == (drainProgress != null));
        AssertUtils.domainPropTrue("new broker operation requires route endpoint",
                type != BrokerClusterOperationType.ADD_BROKER || routeEndpoint != null);
        AssertUtils.domainPropTrue("learner join operation requires membership endpoint",
                (type != BrokerClusterOperationType.START_LEARNER_SYNC
                        && type != BrokerClusterOperationType.ADD_LEARNER) || membershipEndpoint != null);
        AssertUtils.domainPropTrue("membership reduction requires frozen minimum voter count",
                (type != BrokerClusterOperationType.DEMOTE_VOTER
                        && type != BrokerClusterOperationType.DEMOTE_BROKER
                        && type != BrokerClusterOperationType.REMOVE_BROKER) || minimumVoterCount != null);
    }

    public static final class Builder {
        private final BrokerClusterOperation operation = new BrokerClusterOperation();

        public Builder id(ClusterOperationId value) { operation.id = value; return this; }
        public Builder commandDigest(CommandDigest value) {
            operation.commandDigest = value;
            return this;
        }
        public Builder type(BrokerClusterOperationType value) {
            operation.type = value;
            return this;
        }
        public Builder targetBrokerId(BrokerId value) {
            operation.targetBrokerId = value;
            return this;
        }
        public Builder acceptedEpoch(RoutingEpoch value) {
            operation.acceptedEpoch = value;
            return this;
        }
        public Builder acceptedRevision(ClusterRevision value) {
            operation.acceptedRevision = value;
            return this;
        }
        public Builder resultEpoch(RoutingEpoch value) {
            operation.resultEpoch = value;
            return this;
        }
        public Builder phase(BrokerClusterOperationPhase value) {
            operation.phase = value;
            return this;
        }
        public Builder error(OperationFailureReason value) {
            operation.error = value;
            return this;
        }
        public Builder createdAt(Instant value) { operation.createdAt = value; return this; }
        public Builder completedAt(Instant value) { operation.completedAt = value; return this; }
        public Builder routeEndpoint(BrokerRouteEndpoint value) {
            operation.routeEndpoint = value;
            return this;
        }
        public Builder minimumVoterCount(MinimumVoterCount value) {
            operation.minimumVoterCount = value;
            return this;
        }
        public Builder drainProgress(DrainProgress value) {
            operation.drainProgress = value;
            return this;
        }
        public Builder membershipEndpoint(MembershipEndpoint value) {
            operation.membershipEndpoint = value;
            return this;
        }

        public BrokerClusterOperation build() {
            operation.validate();
            return operation;
        }
    }
}
