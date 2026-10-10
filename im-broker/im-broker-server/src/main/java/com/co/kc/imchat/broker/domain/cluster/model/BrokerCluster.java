package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseRejectionReason;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Broker 权威集群聚合根，是拓扑与路由代次的一致性边界。
 *
 * <p>聚合统一保护拓扑 revision、路由 epoch、管理 operation 与旧 epoch fence。具名领域行为保证 Broker
 * 身份和端点稳定、成员与流量状态迁移合法、operation 阶段单调以及 fence 证据不回退。</p>
 */
public final class BrokerCluster {

    /**
     * 建群时确定的 RouteBroker 选择算法。
     */
    @Getter
    @Accessors(fluent = true)
    private BrokerSelectionAlgorithm algorithm;
    /**
     * 聚合内允许保留的 operation 历史上限。
     */
    private OperationHistoryCapacity operationHistoryCapacity;
    /**
     * 按 operationId 保持提交顺序的 operation 历史。
     */
    private Map<ClusterOperationId, BrokerClusterOperation> operations;
    /* 已提交的 Broker 身份到 Raft 成员通信地址的绑定。 */
    private Map<BrokerId, MembershipEndpoint> membershipEndpoints;
    /**
     * 集群状态的单调 revision。
     */
    @Getter
    @Accessors(fluent = true)
    private ClusterRevision revision;
    /**
     * 当前 RouteBroker 映射代次。
     */
    @Getter
    @Accessors(fluent = true)
    private RoutingEpoch epoch;
    /**
     * 当前已提交的 Broker 拓扑。
     */
    private List<BrokerNode> brokers;
    /**
     * 最近一次已提交的路由隔离事实；尚未登记时为空。
     */
    private RoutingEpochFence routingFence;

    private BrokerCluster() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean exists(ClusterOperationId operationId) {
        return operations.containsKey(operationId);
    }

    public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
        return Optional.ofNullable(operations.get(operationId));
    }

    /**
     * 查找节点的独立状态副本；对返回值执行实体行为不会改变当前聚合。
     */
    public Optional<BrokerNode> findNode(BrokerId brokerId) {
        return brokers.stream()
                .filter(broker -> broker.id().equals(brokerId))
                .findFirst()
                .map(BrokerNode::copy);
    }

    /**
     * 返回分离的节点状态，调用者不能经读取接口修改权威拓扑。
     */
    public List<BrokerNode> brokers() {
        return brokers.stream().map(BrokerNode::copy).toList();
    }

    /** 返回当前参与路由的 ACTIVE 节点副本，供 Lease 授权和路由隔离确定目标集合。 */
    public List<BrokerNode> activeBrokers() {
        return brokers.stream()
                .filter(BrokerNode::participatesInRouting)
                .map(BrokerNode::copy)
                .toList();
    }

    public List<BrokerClusterOperation> operations() {
        return List.copyOf(operations.values());
    }

    /** 查询已提交的路由隔离事实；尚未登记隔离事实时返回空。 */
    public Optional<RoutingEpochFence> findRoutingFence() {
        return Optional.ofNullable(routingFence);
    }

    /**
     * 检查目标 Broker 在请求 epoch 的签发资格，不修改聚合或分配租约序列。
     * epoch 必须匹配、目标必须参与路由且 epoch 尚未关闭签发；通过时返回空，否则保留拒绝类型及原因。
     */
    public Optional<LeaseGrantResult> checkLeaseGrant(BrokerId brokerId, RoutingEpoch requestedEpoch) {
        if (!epoch.equals(requestedEpoch)) {
            return Optional.of(LeaseGrantResult.denied(
                    new LeaseRejectionReason("lease request does not match committed state")));
        }
        boolean active = brokers.stream().anyMatch(broker ->
                broker.id().equals(brokerId) && broker.participatesInRouting());
        if (!active) {
            return Optional.of(LeaseGrantResult.denied(
                    new LeaseRejectionReason("broker is not ACTIVE in committed state")));
        }
        if (routingFence != null && routingFence.epoch().value() >= requestedEpoch.value()) {
            return Optional.of(LeaseGrantResult.fenced(new LeaseRejectionReason("epoch grant is closed")));
        }
        return Optional.empty();
    }

    public Map<BrokerId, MembershipEndpoint> membershipEndpoints() {
        return Map.copyOf(membershipEndpoints);
    }

    public boolean hasMembershipEndpoint(BrokerId brokerId, MembershipEndpoint endpoint) {
        return endpoint.equals(membershipEndpoints.get(brokerId));
    }

    /**
     * 返回节点状态与权威聚合隔离的深快照，供应用校验与领域操作使用。
     */
    public BrokerCluster snapshot() {
        return builder()
                .revision(revision)
                .epoch(epoch)
                .algorithm(algorithm)
                .brokers(brokers)
                .operations(operations())
                .membershipEndpoints(membershipEndpoints)
                .routingFence(routingFence)
                .operationHistoryCapacity(operationHistoryCapacity)
                .build();
    }

    /**
     * BrokerCluster 聚合恢复 Builder。
     */
    public static final class Builder {
        private final BrokerCluster cluster = new BrokerCluster();
        private List<BrokerClusterOperation> operationSnapshot;

        public Builder revision(ClusterRevision value) {
            cluster.revision = value;
            return this;
        }

        public Builder epoch(RoutingEpoch value) {
            cluster.epoch = value;
            return this;
        }

        public Builder algorithm(BrokerSelectionAlgorithm value) {
            cluster.algorithm = value;
            return this;
        }

        public Builder brokers(List<BrokerNode> value) {
            cluster.brokers = value;
            return this;
        }

        public Builder operations(List<BrokerClusterOperation> value) {
            operationSnapshot = value;
            return this;
        }

        public Builder membershipEndpoints(Map<BrokerId, MembershipEndpoint> value) {
            cluster.membershipEndpoints = value;
            return this;
        }

        public Builder routingFence(RoutingEpochFence value) {
            cluster.routingFence = value;
            return this;
        }

        public Builder operationHistoryCapacity(OperationHistoryCapacity value) {
            cluster.operationHistoryCapacity = value;
            return this;
        }

        public BrokerCluster build() {
            AssertUtils.allDomainPropNotNull("broker cluster builder is incomplete",
                    cluster.revision, cluster.epoch, cluster.algorithm,
                    cluster.brokers, operationSnapshot, cluster.operationHistoryCapacity);
            cluster.brokers = cluster.validateAndSortBrokers(cluster.brokers);
            cluster.operations = cluster.restoreOperationHistory(operationSnapshot, cluster.operationHistoryCapacity);
            cluster.membershipEndpoints = new LinkedHashMap<>(cluster.membershipEndpoints == null ? Map.of() : cluster.membershipEndpoints);
            if (!cluster.brokers.stream().map(BrokerNode::id).collect(Collectors.toSet()).containsAll(cluster.membershipEndpoints.keySet())) {
                throw new IllegalArgumentException("membership endpoint is bound to an unknown broker");
            }
            if (Set.copyOf(cluster.membershipEndpoints.values()).size() != cluster.membershipEndpoints.size()) {
                throw new IllegalArgumentException("membership endpoint is bound to multiple brokers");
            }
            cluster.validateFence(cluster.routingFence);
            return cluster;
        }
    }

    /**
     * 按 Begin 已登记的事实执行实体行为，Commit 不接收或生成目标拓扑。
     */
    public BrokerClusterDecision commitOperation(ClusterOperationId operationId,
                                                 CommandDigest commandDigest,
                                                 ClusterRevision expectedRevision,
                                                 Instant changedAt) {
        BrokerClusterOperation existing = operations.get(operationId);
        if (existing == null) {
            return BrokerClusterDecision.rejected(null,
                    new DecisionReason("cluster operation must be committed before topology"));
        }
        if (!existing.commandDigest().equals(commandDigest)) {
            return BrokerClusterDecision.rejected(existing,
                    new DecisionReason("operationId is already bound to another request"));
        }
        if (existing.phase() == BrokerClusterOperationPhase.FAILED) {
            return BrokerClusterDecision.rejected(existing,
                    new DecisionReason("failed operation cannot commit topology"));
        }
        if (existing.phase() != BrokerClusterOperationPhase.PENDING
                && existing.phase() != BrokerClusterOperationPhase.FENCING_OLD_EPOCH) {
            return BrokerClusterDecision.duplicate(existing,
                    new DecisionReason("operation was already committed"));
        }
        if (!existing.acceptedRevision().equals(expectedRevision)
                || !revision.equals(expectedRevision.next())) {
            return BrokerClusterDecision.conflict(existing,
                    new DecisionReason("expected revision does not match committed state"));
        }
        if (!existing.resultEpoch().equals(epoch) && !hasCommittedFence(operationId)) {
            return BrokerClusterDecision.conflict(existing,
                    new DecisionReason("old routing epoch fence is not committed for operation"));
        }

        BrokerClusterOperation operation = existing.advance(
                BrokerClusterOperationPhase.RAFT_COMMITTED,
                null,
                changedAt);
        // 所有可能失败的校验与数值计算先完成，节点行为只在 Commit 内执行。
        Optional<DecisionReason> violation = checkOperationConstraints(existing.type(),
                existing.targetBrokerId(), existing.routeEndpoint(), existing.minimumVoterCount());
        if (violation.isPresent()) {
            return BrokerClusterDecision.rejected(existing, violation.get());
        }
        ClusterRevision committedRevision;
        try {
            committedRevision = revision.next();
        } catch (ArithmeticException exception) {
            return BrokerClusterDecision.rejected(existing, new DecisionReason(exception.getMessage()));
        }
        BrokerNode broker = brokers.stream().filter(node -> node.id().equals(existing.targetBrokerId()))
                .findFirst().orElse(null);
        switch (existing.type()) {
            case ADD_BROKER -> brokers = java.util.stream.Stream.concat(brokers.stream(),
                            java.util.stream.Stream.of(BrokerNode.discovered(existing.targetBrokerId(),
                                    existing.routeEndpoint())))
                    .sorted(Comparator.comparing(node -> node.id().value())).toList();
            case ADD_LEARNER, START_LEARNER_SYNC -> broker.joinAsLearner();
            case COMPLETE_LEARNER_SYNC -> broker.completeLearnerSync();
            case PROMOTE_LEARNER, PROMOTE_BROKER -> broker.promoteToVoter();
            case DEMOTE_VOTER, DEMOTE_BROKER -> broker.demoteToLearner();
            case ACTIVATE_BROKER -> broker.activate();
            case DEACTIVATE_BROKER, FAIL_BROKER -> broker.deactivate();
            case DRAIN_BROKER -> broker.drain();
            case REMOVE_BROKER -> brokers = brokers.stream()
                    .filter(node -> !node.id().equals(existing.targetBrokerId())).toList();
        }
        if (existing.type() == BrokerClusterOperationType.REMOVE_BROKER) {
            membershipEndpoints.remove(existing.targetBrokerId());
        }
        revision = committedRevision;
        epoch = existing.resultEpoch();
        operations.put(operation.id(), operation);
        return BrokerClusterDecision.applied(operation);
    }

    /**
     * 校验并登记 Broker 激活事实，不修改节点流量状态。
     *
     * <p>激活会改变 RouteBroker 选择输入，因此这里只创建进入旧 epoch fence 阶段的 operation，
     * 不立即修改当前拓扑。</p>
     */
    public BrokerClusterDecision activate(BrokerActivationIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.ACTIVATE_BROKER)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .build());
    }

    /**
     * 校验并登记停用事实，重复请求由已有操作识别。
     */
    public BrokerClusterDecision deactivate(BrokerDeactivationIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.DEACTIVATE_BROKER)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .build());
    }

    /**
     * 摘除 Broker 新流量，并冻结 Gateway 路由排空证据。
     */
    public BrokerClusterDecision drain(BrokerDrainIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.DRAIN_BROKER)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .initialDrainProgress(intent.progress())
                .build());
    }

    /**
     * 故障摘除 Broker 路由能力，并保留故障恢复所需的 drain 证据。
     */
    public BrokerClusterDecision removeFailedBroker(BrokerFailureRemovalIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.FAIL_BROKER)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .initialDrainProgress(intent.progress())
                .build());
    }

    /**
     * 将 DISCOVERED Broker 登记为 learner syncing，实际成员变更由应用层 Adapter 执行。
     */
    public BrokerClusterDecision addLearner(LearnerJoinIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.START_LEARNER_SYNC)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .membershipEndpoint(intent.membershipEndpoint())
                .build());
    }

    /**
     * 登记 learner 已完成同步的事实，Commit 才修改角色与流量状态。
     */
    public BrokerClusterDecision confirmLearner(LearnerSyncCompletionIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.COMPLETE_LEARNER_SYNC)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .build());
    }

    /**
     * 登记 learner 晋升 voter 的意图，实际成员变更由应用层执行。
     */
    public BrokerClusterDecision promoteLearner(LearnerPromotionIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.PROMOTE_LEARNER)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .build());
    }

    /**
     * 校验并登记降级事实，冻结本次操作的最低 voter 数量。
     */
    public BrokerClusterDecision demoteVoter(VoterDemotionIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.DEMOTE_VOTER)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .minimumVoterCount(intent.minimumVoterCount())
                .build());
    }

    /**
     * 校验并登记移除事实，节点只有在 Commit 时才离开当前拓扑。
     */
    public BrokerClusterDecision removeBroker(BrokerRemovalIntent intent) {
        return submitOperation(BrokerClusterOperationProposal.builder()
                .operationId(intent.operationId())
                .commandDigest(intent.commandDigest())
                .expectedRevision(intent.expectedRevision())
                .operationType(BrokerClusterOperationType.REMOVE_BROKER)
                .targetBrokerId(intent.brokerId())
                .changedAt(intent.changedAt())
                .minimumVoterCount(intent.minimumVoterCount())
                .build());
    }

    /**
     * Begin 重放入口：校验并登记恢复事实，节点状态只由 Commit 修改。
     */
    public BrokerClusterDecision submitOperation(BrokerClusterOperationProposal proposal) {
        // 1. 查重：重试复用已登记事实，不重新检查旧 revision 和节点状态。
        BrokerClusterOperation existing = operations.get(proposal.operationId());
        if (existing != null) {
            return existing.checkRepeatedIntent(proposal);
        }

        // 2. 准入：只做判断，不修改集群或操作历史。
        Optional<BrokerClusterDecision> rejection = checkAdmission(proposal);
        if (rejection.isPresent()) {
            return rejection.get();
        }

        // 3. 创建：完成生命周期初始化与 revision 计算，失败不能留下部分登记。
        BrokerClusterOperation operation = BrokerClusterOperation.begin(proposal, revision, epoch);
        ClusterRevision nextRevision = revision.next();

        // 4. 容量：必要时回收最早的终态记录，未完成操作不能被回收。
        if (!ensureOperationCapacity()) {
            return BrokerClusterDecision.unavailable(
                    new DecisionReason("operation history is full of non-terminal operations"));
        }

        // 5. 登记：保存冻结事实、成员端点与 revision；节点修改留给 Commit。
        operations.put(operation.id(), operation);
        if (operation.learnerJoin()) {
            membershipEndpoints.put(operation.targetBrokerId(), operation.membershipEndpoint());
        }
        revision = nextRevision;
        return BrokerClusterDecision.applied(operation);
    }

    /**
     * 新操作的准入判断，按 revision、端点绑定、并行操作、节点规则的顺序返回拒绝结果。
     */
    private Optional<BrokerClusterDecision> checkAdmission(BrokerClusterOperationProposal proposal) {
        // 1. 新请求必须基于当前 revision。
        if (!revision.equals(proposal.expectedRevision())) {
            return Optional.of(BrokerClusterDecision.conflict(null,
                    new DecisionReason("expected revision does not match committed state")));
        }

        // 2. 检查成员端点绑定，保持具体规则只返回原因。
        if (proposal.learnerJoin()) {
            Optional<DecisionReason> violation = checkEndpointBinding(
                    proposal.targetBrokerId(), proposal.membershipEndpoint());
            if (violation.isPresent()) {
                return Optional.of(BrokerClusterDecision.rejected(null, violation.get()));
            }
        }

        // 3. 共识中的操作生命周期决定互斥，不依赖 Leader 进程内标记。
        if (operations.values().stream().anyMatch(BrokerClusterOperation::blocksNewOperation)) {
            return Optional.of(BrokerClusterDecision.conflict(null,
                    new DecisionReason("another cluster change is awaiting topology commit or authorization")));
        }

        // 4. 校验节点资格、流量状态和最低 voter 数量；这一阶段不修改节点。
        Optional<DecisionReason> violation = checkOperationConstraints(
                proposal.operationType(), proposal.targetBrokerId(),
                proposal.routeEndpoint(), proposal.minimumVoterCount());
        return violation.map(decisionReason -> BrokerClusterDecision.rejected(null, decisionReason));
    }


    public BrokerClusterDecision advanceOperation(ClusterOperationId operationId,
                                                  CommandDigest commandDigest,
                                                  BrokerClusterOperationPhase expectedPhase,
                                                  BrokerClusterOperationPhase nextPhase,
                                                  OperationFailureReason detail,
                                                  Instant changedAt) {
        BrokerClusterOperation operation = operations.get(operationId);
        if (operation == null) {
            return BrokerClusterDecision.rejected(null, new DecisionReason("operation does not exist"));
        }
        if (!operation.commandDigest().equals(commandDigest)) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason("operationId is already bound to another request"));
        }
        if (operation.type() == BrokerClusterOperationType.FAIL_BROKER
                && (nextPhase == BrokerClusterOperationPhase.VERIFYING
                || nextPhase == BrokerClusterOperationPhase.SUCCEEDED
                || nextPhase == BrokerClusterOperationPhase.DEGRADED)) {
            return BrokerClusterDecision.rejected(operation,
                    new DecisionReason("route recovery requires verification or completion behavior"));
        }
        if (operation.phase() == nextPhase) {
            return BrokerClusterDecision.duplicate(operation, new DecisionReason("operation phase was already committed"));
        }
        if (operation.phase() != expectedPhase) {
            return BrokerClusterDecision.conflict(operation, new DecisionReason("operation phase does not match expected phase"));
        }
        // 先构造合法的下一阶段；领域拒绝不能逃逸为 Raft 状态机执行错误。
        BrokerClusterOperation advanced;
        try {
            advanced = operation.advance(nextPhase, detail, changedAt);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason(exception.getMessage()));
        }
        operations.put(advanced.id(), advanced);
        if ((operation.type() == BrokerClusterOperationType.START_LEARNER_SYNC
                || operation.type() == BrokerClusterOperationType.ADD_LEARNER)
                && nextPhase == BrokerClusterOperationPhase.FAILED) {
            membershipEndpoints.remove(operation.targetBrokerId());
        }
        return BrokerClusterDecision.applied(advanced);
    }

    public BrokerClusterDecision commitRoutingFence(RoutingEpochFence operation) {
        if (operation == null) {
            throw new IllegalArgumentException("routing epoch fence is required");
        }
        BrokerClusterOperation clusterOperation = operations.get(operation.operationId());
        if (clusterOperation == null
                || clusterOperation.phase() != BrokerClusterOperationPhase.FENCING_OLD_EPOCH) {
            return BrokerClusterDecision.rejected(clusterOperation,
                    new DecisionReason("fence requires a matching cluster operation"));
        }
        if (!epoch.equals(operation.epoch())) {
            return BrokerClusterDecision.conflict(clusterOperation,
                    new DecisionReason("fence operation does not match committed cluster state"));
        }
        // 在日志应用边界核对隔离集合，不能仅依赖提交前读取的拓扑或 revision。
        List<BrokerId> activeIds = activeBrokerIds(brokers).stream()
                .sorted(Comparator.comparing(BrokerId::value)).toList();
        if (!operation.fencedBrokerIds().equals(activeIds)) {
            return BrokerClusterDecision.conflict(clusterOperation,
                    new DecisionReason("fence brokers do not match committed ACTIVE brokers"));
        }
        if (routingFence == null) {
            routingFence = operation;
            return BrokerClusterDecision.applied(clusterOperation);
        }
        if (routingFence.equals(operation)) {
            return BrokerClusterDecision.duplicate(clusterOperation,
                    new DecisionReason("fence operation was already committed"));
        }
        if (canResumeFence(routingFence, operation)) {
            routingFence = operation;
            return BrokerClusterDecision.applied(clusterOperation);
        }
        if (routingFence.epoch().value() >= operation.epoch().value()) {
            return BrokerClusterDecision.rejected(clusterOperation,
                    new DecisionReason("routing epoch is already fenced"));
        }
        routingFence = operation;
        return BrokerClusterDecision.applied(clusterOperation);
    }

    public BrokerClusterDecision updateDrainProgress(
            ClusterOperationId operationId,
            CommandDigest commandDigest,
            DrainProgressUpdate progressUpdate,
            Instant changedAt) {
        BrokerClusterOperation existing = operations.get(operationId);
        BrokerClusterDecision invalid = validateOperationIdentity(
                existing, commandDigest);
        if (invalid != null) {
            return invalid;
        }
        if (existing.phase() != BrokerClusterOperationPhase.REBUILDING_ROUTES
                && existing.phase() != BrokerClusterOperationPhase.VERIFYING) {
            return BrokerClusterDecision.conflict(existing,
                    new DecisionReason("drain operation is not rebuilding routes"));
        }
        // 故障重建证据必须对应日志应用时的检查点，不能保存提交前读取的过期汇总。
        if (existing.type() == BrokerClusterOperationType.FAIL_BROKER
                && progressUpdate.kind() == DrainProgressUpdate.Kind.SUMMARIZE
                && !existing.drainProgress().summarizeRebuild().equals(progressUpdate.recoveryEvidence())) {
            return BrokerClusterDecision.rejected(existing,
                    new DecisionReason("recovery evidence does not match current Gateway checkpoints"));
        }
        try {
            DrainProgress updatedProgress = progressUpdate
                    .applyTo(existing.drainProgress());
            BrokerClusterOperation updated = existing.updateDrainProgress(updatedProgress);
            if (updated.phase() == BrokerClusterOperationPhase.REBUILDING_ROUTES
                    && updatedProgress.readyToComplete(changedAt)) {
                updated = updated.advance(BrokerClusterOperationPhase.VERIFYING,
                        (OperationFailureReason) null, changedAt);
            }
            operations.put(updated.id(), updated);
            return updated.equals(existing)
                    ? BrokerClusterDecision.duplicate(updated,
                    new DecisionReason("drain progress was already committed"))
                    : BrokerClusterDecision.applied(updated);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return BrokerClusterDecision.rejected(existing,
                    new DecisionReason(exception.getMessage()));
        }
    }

    /** 在日志应用边界核验当前恢复证据、ACK 和期限，登记正常或降级完成意图。 */
    public BrokerClusterDecision verifyRouteRecovery(ClusterOperationId operationId, CommandDigest commandDigest,
                                                     Duration recoveryTimeout, Instant changedAt) {
        BrokerClusterOperation operation = operations.get(operationId);
        BrokerClusterDecision invalid = validateOperationIdentity(operation, commandDigest);
        if (invalid != null) {
            return invalid;
        }
        try {
            BrokerClusterOperation verified = operation.verifyRouteRecovery(recoveryTimeout, changedAt);
            operations.put(verified.id(), verified);
            return verified.equals(operation)
                    ? BrokerClusterDecision.duplicate(verified, new DecisionReason("route recovery was already verified"))
                    : BrokerClusterDecision.applied(verified);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason(exception.getMessage()));
        }
    }

    /** 依据聚合当前持有的恢复意图选择终态，重复完成不修改已提交事实。 */
    public BrokerClusterDecision completeRouteRecovery(ClusterOperationId operationId, CommandDigest commandDigest,
                                                       Instant changedAt) {
        BrokerClusterOperation operation = operations.get(operationId);
        BrokerClusterDecision invalid = validateOperationIdentity(operation, commandDigest);
        if (invalid != null) {
            return invalid;
        }
        try {
            BrokerClusterOperation completed = operation.completeRouteRecovery(changedAt);
            operations.put(completed.id(), completed);
            return completed.equals(operation)
                    ? BrokerClusterDecision.duplicate(completed, new DecisionReason("route recovery was already completed"))
                    : BrokerClusterDecision.applied(completed);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason(exception.getMessage()));
        }
    }

    public BrokerClusterDecision completeDrain(ClusterOperationId operationId,
                                               CommandDigest commandDigest,
                                               ClusterRevision expectedRevision,
                                               Instant changedAt) {
        return finishDrain(operationId, commandDigest, expectedRevision, null,
                changedAt, false);
    }

    public BrokerClusterDecision forceDrain(ClusterOperationId operationId,
                                            CommandDigest commandDigest,
                                            ClusterRevision expectedRevision,
                                            OperationFailureReason reason,
                                            Instant changedAt) {
        return finishDrain(operationId, commandDigest, expectedRevision, reason,
                changedAt, true);
    }

    private BrokerClusterDecision finishDrain(ClusterOperationId operationId,
                                              CommandDigest commandDigest,
                                              ClusterRevision expectedRevision,
                                              OperationFailureReason reason,
                                              Instant changedAt,
                                              boolean force) {
        BrokerClusterOperation existing = operations.get(operationId);
        BrokerClusterDecision invalid = validateOperationIdentity(existing, commandDigest);
        if (invalid != null) {
            return invalid;
        }
        BrokerClusterOperationPhase terminalPhase = force
                ? BrokerClusterOperationPhase.FORCED : BrokerClusterOperationPhase.SUCCEEDED;
        if (existing.phase() == terminalPhase) {
            return BrokerClusterDecision.duplicate(existing,
                    new DecisionReason("drain operation was already completed"));
        }
        if (force) {
            if (existing.phase() != BrokerClusterOperationPhase.FAILED) {
                return BrokerClusterDecision.conflict(existing,
                        new DecisionReason("only FAILED drain operation can be forced"));
            }
        } else if (existing.phase() != BrokerClusterOperationPhase.VERIFYING
                || !existing.drainProgress().readyToComplete(
                changedAt)) {
            return BrokerClusterDecision.conflict(existing,
                    new DecisionReason("drain operation is not ready to complete"));
        }
        if (!revision.equals(expectedRevision)) {
            return BrokerClusterDecision.conflict(existing,
                    new DecisionReason("expected revision does not match committed state"));
        }

        BrokerNode target = brokers.stream()
                .filter(node -> node.id().equals(existing.targetBrokerId()))
                .findFirst().orElse(null);
        if (target == null || target.trafficState() != BrokerTrafficState.DRAINING) {
            return BrokerClusterDecision.rejected(existing,
                    new DecisionReason("drain target must be DRAINING"));
        }
        try {
            BrokerClusterOperation completed = existing.advance(
                    terminalPhase, reason, changedAt);
            ClusterRevision completedRevision = revision.next();
            target.finishDrain();
            revision = completedRevision;
            operations.put(completed.id(), completed);
            return BrokerClusterDecision.applied(completed);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return BrokerClusterDecision.rejected(existing,
                    new DecisionReason(exception.getMessage()));
        }
    }

    private BrokerClusterDecision validateOperationIdentity(
            BrokerClusterOperation existing,
            CommandDigest commandDigest) {
        if (existing == null) {
            return BrokerClusterDecision.rejected(null,
                    new DecisionReason("operation does not exist"));
        }
        if (!existing.commandDigest().equals(commandDigest)) {
            return BrokerClusterDecision.rejected(existing,
                    new DecisionReason("operationId is already bound to another request"));
        }
        return null;
    }

    private boolean canResumeFence(RoutingEpochFence existing,
                                   RoutingEpochFence replacement) {
        return existing.operationId().equals(replacement.operationId())
                && existing.epoch().equals(replacement.epoch())
                && existing.fencedBrokerIds().equals(replacement.fencedBrokerIds())
                && replacement.leaderTerm().compareTo(existing.leaderTerm()) > 0
                && replacement.quorumReadIndex().compareTo(existing.quorumReadIndex()) > 0;
    }

    /**
     * 确保可登记一项新操作；满额时回收最早的终态记录，全部未完成时返回 false。
     */
    private boolean ensureOperationCapacity() {
        if (operations.size() < operationHistoryCapacity.value()) {
            return true;
        }
        Iterator<Map.Entry<ClusterOperationId, BrokerClusterOperation>> iterator = operations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().phase().terminal()) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }


    /**
     * 校验操作的当前节点条件和集群不变量。Begin 与 Commit 重放共用，不修改任何状态。
     */
    private Optional<DecisionReason> checkOperationConstraints(
            BrokerClusterOperationType type, BrokerId brokerId,
            BrokerRouteEndpoint routeEndpoint, MinimumVoterCount minimumVoterCount) {
        BrokerNode broker = brokers.stream().filter(node -> node.id().equals(brokerId)).findFirst().orElse(null);
        if (type == BrokerClusterOperationType.ADD_BROKER) {
            if (broker != null) {
                return Optional.of(new DecisionReason("broker cluster change has no effect"));
            }
            if (routeEndpoint == null) {
                return Optional.of(new DecisionReason("new broker route endpoint is required"));
            }
            return Optional.empty();
        }
        if (broker == null) {
            return Optional.of(new DecisionReason("target broker does not exist"));
        }
        if (routeEndpoint != null) {
            return Optional.of(new DecisionReason("route endpoint cannot change for existing broker"));
        }
        // 节点资格属于实体；聚合只继续检查跨节点数量约束。
        Optional<DecisionReason> violation = broker.checkOperation(type);
        if (violation.isPresent()) {
            return violation;
        }
        boolean removesRoutingTraffic = type == BrokerClusterOperationType.DEACTIVATE_BROKER
                || type == BrokerClusterOperationType.DRAIN_BROKER
                || type == BrokerClusterOperationType.FAIL_BROKER;
        if (removesRoutingTraffic && brokers.stream().filter(BrokerNode::participatesInRouting).count() <= 1L) {
            return Optional.of(new DecisionReason("broker cluster must contain at least one ACTIVE broker"));
        }
        if (type == BrokerClusterOperationType.DEMOTE_VOTER
                || type == BrokerClusterOperationType.DEMOTE_BROKER
                || type == BrokerClusterOperationType.REMOVE_BROKER) {
            if (minimumVoterCount == null) {
                return Optional.of(new DecisionReason("minimum voter count is required"));
            }
            if (broker.membershipRole() == BrokerMembershipRole.VOTER
                    && brokers.stream().filter(node -> node.membershipRole() == BrokerMembershipRole.VOTER)
                    .filter(node -> !node.id().equals(brokerId)).count() < minimumVoterCount.value()) {
                return Optional.of(new DecisionReason("membership change violates minimum voter count "
                        + minimumVoterCount.value()));
            }
        }
        return Optional.empty();
    }

    /**
     * 检查成员端点绑定，不修改状态；空结果表示没有违规。
     */
    private Optional<DecisionReason> checkEndpointBinding(
            BrokerId brokerId, MembershipEndpoint endpoint) {
        if (endpoint == null) {
            return Optional.of(new DecisionReason("learner membership endpoint is required"));
        }
        MembershipEndpoint boundEndpoint = membershipEndpoints.get(brokerId);
        if (boundEndpoint != null && !boundEndpoint.equals(endpoint)) {
            return Optional.of(new DecisionReason("broker is bound to another membership endpoint"));
        }
        boolean occupiedByAnotherBroker = membershipEndpoints.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(brokerId) && entry.getValue().equals(endpoint));
        if (occupiedByAnotherBroker) {
            return Optional.of(new DecisionReason("membership endpoint is bound to another broker"));
        }
        return Optional.empty();
    }

    private boolean hasCommittedFence(ClusterOperationId operationId) {
        return routingFence != null
                && routingFence.operationId().equals(operationId)
                && routingFence.epoch().equals(epoch)
                && routingFence.fencedBrokerIds().equals(activeBrokerIds(brokers).stream()
                .sorted(Comparator.comparing(BrokerId::value)).toList());
    }


    private Set<BrokerId> activeBrokerIds(List<BrokerNode> brokerNodes) {
        return brokerNodes.stream()
                .filter(BrokerNode::participatesInRouting)
                .map(BrokerNode::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<BrokerNode> validateAndSortBrokers(List<BrokerNode> brokerNodes) {
        if (brokerNodes.isEmpty()) {
            throw new IllegalArgumentException("broker cluster must contain brokers");
        }
        for (BrokerNode broker : brokerNodes) {
            if (broker == null) {
                throw new IllegalArgumentException("broker cluster contains null broker");
            }
        }
        List<BrokerNode> normalized = brokerNodes.stream()
                .map(BrokerNode::copy)
                .sorted(Comparator.comparing(broker -> broker.id().value()))
                .toList();
        Set<BrokerId> brokerIds = normalized.stream().map(BrokerNode::id)
                .collect(Collectors.toSet());
        if (brokerIds.size() != normalized.size()) {
            throw new IllegalArgumentException("broker cluster contains duplicate broker id");
        }
        if (normalized.stream().noneMatch(BrokerNode::participatesInRouting)) {
            throw new IllegalArgumentException(
                    "broker cluster must contain at least one ACTIVE broker");
        }
        return normalized;
    }

    private Map<ClusterOperationId, BrokerClusterOperation> restoreOperationHistory(
            List<BrokerClusterOperation> restoredOperations,
            OperationHistoryCapacity historyCapacity) {
        if (restoredOperations.size() > historyCapacity.value()) {
            throw new IllegalArgumentException("operation history is invalid");
        }
        Map<ClusterOperationId, BrokerClusterOperation> restored = new LinkedHashMap<>();
        for (BrokerClusterOperation operation : restoredOperations) {
            if (operation == null) {
                throw new IllegalArgumentException("operation history contains null operation");
            }
            if (restored.put(operation.id(), operation) != null) {
                throw new IllegalArgumentException("operation history contains duplicate id");
            }
        }
        return restored;
    }

    private void validateFence(RoutingEpochFence operation) {
        if (operation != null
                && operation.epoch().value() > epoch.value()) {
            throw new IllegalArgumentException("fence does not belong to broker cluster state");
        }
    }
}
