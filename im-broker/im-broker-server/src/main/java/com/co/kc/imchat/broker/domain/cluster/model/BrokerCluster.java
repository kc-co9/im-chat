package com.co.kc.imchat.broker.domain.cluster.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseRejectionReason;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Broker 权威集群聚合根，是拓扑与路由代次的一致性边界。
 *
 * <p>聚合统一保护拓扑 revision、路由 epoch、管理 operation 与旧 epoch fence。具名领域行为保证 Broker
 * 身份和端点稳定、成员与流量状态迁移合法、operation 阶段单调以及 fence 证据不回退。</p>
 */
public final class BrokerCluster {

    /* 角色转换的中间步骤：先移除旧角色，再登记最终角色。 */
    private static final String REMOVE_OLD_ROLE_STEP = "remove-old-role";
    /* 所有成员变更的最终配置步骤。 */
    private static final String FINAL_TARGET_STEP = "final-target";

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

    /**
     * 从同一已提交业务快照解析完整 VOTER 观察者；成员过渡或绑定缺失时返回空集合。
     */
    public Set<BrokerId> findObservers() {
        /* 1. 成员过渡期间不发布观察者集合，避免以不稳定角色计算多数。 */
        if (operations.values().stream().anyMatch(BrokerClusterOperation::isUnfinishedMembershipChange)) {
            return Set.of();
        }
        /* 2. 核对全部节点角色与端点绑定；任一不一致使整份观察资格失效。 */
        Set<BrokerId> voterIds = new HashSet<>();
        for (BrokerNode brokerNode : brokers) {
            switch (brokerNode.membershipRole()) {
                case DISCOVERED -> {
                    if (membershipEndpoints.containsKey(brokerNode.id())) {
                        return Set.of();
                    }
                }
                case LEARNER_SYNCING -> {
                    return Set.of();
                }
                case VOTER -> {
                    if (!membershipEndpoints.containsKey(brokerNode.id())) {
                        return Set.of();
                    }
                    voterIds.add(brokerNode.id());
                }
                case LEARNER -> {
                    if (!membershipEndpoints.containsKey(brokerNode.id())) {
                        return Set.of();
                    }
                }
            }
        }
        return Set.copyOf(voterIds);
    }

    /**
     * 返回不含操作 revision 的已提交业务拓扑快照，不读取原生成员配置。
     */
    public BrokerClusterTopology topology() {
        return new BrokerClusterTopology(epoch, brokers, membershipEndpoints);
    }

    public boolean exists(ClusterOperationId operationId) {
        return operations.containsKey(operationId);
    }

    public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
        return Optional.ofNullable(operations.get(operationId));
    }

    /**
     * 查找唯一未完成的故障摘流操作，供调用方继续恢复。
     *
     * @return 尚未进入终态的 FAIL_BROKER 操作；不存在时返回空
     * @throws IllegalStateException 存在多个未完成故障操作，无法唯一确定恢复目标
     */
    public Optional<BrokerClusterOperation> findUnfinishedFailureOperation() {
        List<BrokerClusterOperation> unfinished = operations.values().stream()
                .filter(operation -> operation.type() == BrokerClusterOperationType.FAIL_BROKER && !operation.phase().terminal())
                .toList();
        if (unfinished.size() > 1) {
            throw new IllegalStateException("multiple unfinished failure operations");
        }
        return unfinished.stream().findFirst();
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

    /**
     * 返回当前参与路由的 ACTIVE 节点副本，供 Lease 授权和路由隔离确定目标集合。
     */
    public List<BrokerNode> activeBrokers() {
        return brokers.stream()
                .filter(BrokerNode::participatesInRouting)
                .map(BrokerNode::copy)
                .toList();
    }

    public List<BrokerClusterOperation> operations() {
        return List.copyOf(operations.values());
    }

    /**
     * 当前是否存在仍独占集群变更、阻塞新操作的已登记操作。
     */
    public boolean hasBlockingOperation() {
        return operations.values().stream().anyMatch(BrokerClusterOperation::blocksNewOperation);
    }

    /**
     * 查询已提交的路由隔离事实；尚未登记隔离事实时返回空。
     */
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

    public Optional<BrokerNode> findNodeByMembershipEndpoint(MembershipEndpoint endpoint) {
        return brokers.stream().filter(node -> hasMembershipEndpoint(node.id(), endpoint))
                .findFirst().map(BrokerNode::copy);
    }

    public boolean hasPendingMembershipOperation() {
        return operations.values().stream().anyMatch(BrokerClusterOperation::isUnfinishedMembershipChange);
    }

    public boolean hasSyncingLearner() {
        return brokers.stream().anyMatch(node -> node.membershipRole() == BrokerMembershipRole.LEARNER_SYNCING);
    }

    public boolean isMembershipTargetVoter(BrokerId brokerId, BrokerMembershipStep step) {
        MembershipEndpoint binding = membershipEndpoints.get(brokerId);
        return binding != null && step.target().voters().contains(binding);
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
     * 校验并登记排空意图及 Gateway 路由证据，Commit 才将节点置为 DRAINING。
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
     * 校验并登记故障摘流意图及恢复证据，Commit 才将节点置为 INACTIVE。
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
     * 登记 DISCOVERED Broker 加入 learner 的意图；应用层执行成员配置变更，Commit 才修改节点角色。
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
        if (operation.nativeMembershipChange()) {
            operation = operation.withMembershipSteps(membershipStepsFor(proposal));
        }
        // 先计算候选 revision，但暂不写回；revision 溢出或容量不足时本次登记必须保持完全无副作用。
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
        // 操作、端点和 revision 一起提交，避免失败路径只推进 revision。
        revision = nextRevision;
        return BrokerClusterDecision.applied(operation);
    }

    /**
     * 从已提交节点及其绑定端点构造期望成员配置，不包含发现态节点。
     */
    public ConsensusMembership expectedMembership() {
        Set<MembershipEndpoint> voters = new HashSet<>();
        Set<MembershipEndpoint> learners = new HashSet<>();
        for (BrokerNode node : brokers) {
            if (node.membershipRole() == BrokerMembershipRole.DISCOVERED) {
                continue;
            }
            MembershipEndpoint endpoint = membershipEndpoints.get(node.id());
            if (endpoint == null) {
                throw new IllegalStateException("committed membership binding is missing");
            }
            if (node.membershipRole() == BrokerMembershipRole.VOTER) {
                voters.add(endpoint);
            } else {
                learners.add(endpoint);
            }
        }
        return new ConsensusMembership(voters, learners);
    }

    /**
     * 为当前成员步骤登记执行许可，冻结目标配置及执行者身份。
     *
     * <p>先按全部历史识别许可重试，再检查当前 revision 和全局排空状态。
     * 登记只保存许可事实，不执行成员变更；重复登记不产生新的执行授权。</p>
     */
    public BrokerClusterDecision registerMembershipPermit(ClusterOperationId operationId, String stepId,
                                                          String permitId, BrokerId executorBrokerId, String executorIncarnation, ClusterRevision expectedRevision) {
        BrokerClusterOperation operation = operations.get(operationId);
        /* 1. 跨操作历史查重；已有许可先识别身份，不受当前 revision 变化影响。 */
        for (BrokerClusterOperation recorded : operations.values()) {
            for (BrokerMembershipStep step : recorded.membershipSteps()) {
                for (BrokerMembershipPermit permit : step.permits()) {
                    if (!permit.permitId().equals(permitId)) {
                        continue;
                    }
                    if (permit.operationId().equals(operationId) && permit.stepId().equals(stepId)
                            && permit.executorBrokerId().equals(executorBrokerId)
                            && permit.executorIncarnation().equals(executorIncarnation)) {
                        return BrokerClusterDecision.duplicate(operation, new DecisionReason("permit was already registered"));
                    }
                    return BrokerClusterDecision.rejected(operation, new DecisionReason("permitId is already bound"));
                }
            }
        }
        /* 2. 仅当前非终态成员操作可登记，且请求必须基于当前 revision。 */
        if (operation == null || !operation.nativeMembershipChange() || operation.phase().terminal()) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason("current native membership operation is required"));
        }
        if (!revision.equals(expectedRevision)) {
            return BrokerClusterDecision.conflict(operation, new DecisionReason("expected revision does not match committed state"));
        }
        /* 3. 目标必须是当前独占步骤；任一历史许可未排空时不能发出下一份许可。 */
        BrokerMembershipStep step = operation.currentMembershipStep();
        if (!operation.blocksNewOperation() || step == null || !step.stepId().equals(stepId)
                || operations.values().stream().flatMap(value -> value.membershipSteps().stream())
                .anyMatch(BrokerMembershipStep::hasUndrainedPermit)) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason("membership step is not available"));
        }
        /* 4. 追加 UNKNOWN 许可，并与聚合 revision 一起更新。 */
        BrokerMembershipPermit permit = new BrokerMembershipPermit(permitId, operationId, stepId, step.target(),
                executorBrokerId, executorIncarnation, BrokerMembershipPermit.Result.UNKNOWN);
        List<BrokerMembershipPermit> permits = new ArrayList<>(step.permits());
        permits.add(permit);
        return replaceMembershipStep(operation, new BrokerMembershipStep(stepId, step.target(), permits));
    }

    /**
     * 只读检查成员执行结果是否可被接受，不修改许可或聚合 revision。
     *
     * <p>仅步骤的当前完整许可身份可提交已知结果；UNKNOWN 不能作为排空证明。
     * 已排空许可的相同结果返回重复，矛盾结果返回拒绝。</p>
     */
    public BrokerClusterDecision checkMembershipExecutionConfirmation(MembershipExecutionConfirmation confirmation) {
        BrokerClusterOperation operation = operations.get(confirmation.operationId());
        if (operation == null || !operation.nativeMembershipChange() || confirmation.result() == null
                || confirmation.result() == BrokerMembershipPermit.Result.UNKNOWN) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason("known membership result is required"));
        }
        BrokerMembershipStep step = operation.findMembershipStep(confirmation.stepId()).orElse(null);
        BrokerMembershipPermit permit = step == null ? null : step.currentPermit();
        if (permit == null || !permit.permitId().equals(confirmation.permitId())
                || !permit.executorBrokerId().equals(confirmation.executorBrokerId())
                || !permit.executorIncarnation().equals(confirmation.executorIncarnation())
                || !step.targetDigest().equals(confirmation.targetDigest())) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason("current membership permit identity does not match"));
        }
        if (permit.drained()) {
            return permit.result() == confirmation.result()
                    ? BrokerClusterDecision.duplicate(operation, new DecisionReason("permit drain was already confirmed"))
                    : BrokerClusterDecision.rejected(operation, new DecisionReason("permit result is contradictory"));
        }
        if (operation.phase().terminal()) {
            return BrokerClusterDecision.rejected(operation, new DecisionReason("operation is terminal"));
        }
        return BrokerClusterDecision.applied(operation);
    }

    /**
     * 应用执行结果前按最新记录重新检查；只在首次接受时更新记录和 revision。
     */
    public BrokerClusterDecision confirmMembershipExecution(MembershipExecutionConfirmation confirmation) {
        BrokerClusterDecision decision = checkMembershipExecutionConfirmation(confirmation);
        if (decision.status() != BrokerClusterDecision.Status.APPLIED) {
            return decision;
        }
        /* 检查通过后只替换当前许可；重复或拒绝结果不会推进 revision。 */
        BrokerClusterOperation operation = decision.operation();
        BrokerMembershipStep step = operation.findMembershipStep(confirmation.stepId()).orElseThrow();
        BrokerMembershipPermit permit = step.currentPermit();
        List<BrokerMembershipPermit> permits = new ArrayList<>(step.permits());
        permits.set(permits.size() - 1, permit.confirm(confirmation.result()));
        return replaceMembershipStep(operation, new BrokerMembershipStep(step.stepId(), step.target(), permits));
    }

    /**
     * 按 Begin 冻结的操作事实提交节点变化，同时更新 revision、epoch 和操作阶段。
     *
     * <p>Commit 不接收或生成目标拓扑。成员操作须先完成步骤与许可排空；
     * 改变路由代次的操作须先提交当前旧 epoch 的 fence。</p>
     */
    public BrokerClusterDecision commitOperation(ClusterOperationId operationId,
                                                 CommandDigest commandDigest,
                                                 ClusterRevision expectedRevision,
                                                 Instant changedAt) {
        /* 1. 核对已登记身份及阶段；已提交请求直接返回重复。 */
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
        /* 2. 核对成员执行完成事实、对应提交协议的 revision 及旧代次隔离事实。 */
        if (existing.nativeMembershipChange() && !existing.membershipReadyToCommit()) {
            return BrokerClusterDecision.rejected(existing, new DecisionReason("membership steps are not successful and drained"));
        }
        if (existing.nativeMembershipChange()) {
            if (!revision.equals(expectedRevision)) {
                return BrokerClusterDecision.conflict(existing,
                        new DecisionReason("expected revision does not match committed state"));
            }
        } else if (!existing.acceptedRevision().equals(expectedRevision)
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
        /* 3. 重验节点与集群约束并预计算 revision；检查失败不能留下部分节点修改。 */
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
        /* 4. 按冻结操作执行节点行为，再保存端点、版本与阶段变化。 */
        BrokerNode broker = brokers.stream().filter(node -> node.id().equals(existing.targetBrokerId()))
                .findFirst().orElse(null);
        switch (existing.type()) {
            case ADD_BROKER -> brokers = Stream.concat(brokers.stream(),
                            Stream.of(BrokerNode.discovered(existing.targetBrokerId(),
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
     * 推进普通操作阶段，不代替拓扑 Commit 或故障恢复的专用验证、完成行为。
     *
     * <p>先校验身份与专用协议，再识别重复及预期阶段；实体校验通过后才保存。
     * learner 加入失败时同时撤销 Begin 登记的成员端点绑定。</p>
     */
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
        /* 1. 成员拓扑提交必须走 Commit；终态还需满足许可排空和步骤完成规则。 */
        if (operation.nativeMembershipChange()
                && nextPhase == BrokerClusterOperationPhase.RAFT_COMMITTED) {
            return BrokerClusterDecision.rejected(operation,
                    new DecisionReason("membership lifecycle requires permit completion"));
        }
        if (operation.nativeMembershipChange()
                && nextPhase.terminal()
                && operation.membershipSteps().stream().anyMatch(BrokerMembershipStep::hasUndrainedPermit)) {
            return BrokerClusterDecision.rejected(operation,
                    new DecisionReason("membership lifecycle requires permit completion"));
        }
        if (operation.nativeMembershipChange()
                && nextPhase == BrokerClusterOperationPhase.FAILED
                && (!operation.canFailOnRejection()
                || operation.membershipSteps().stream().anyMatch(BrokerMembershipStep::successful))) {
            return BrokerClusterDecision.rejected(operation,
                    new DecisionReason("membership lifecycle requires permit completion"));
        }
        if (operation.nativeMembershipChange()
                && nextPhase.terminal()
                && nextPhase != BrokerClusterOperationPhase.FAILED
                && !operation.membershipReadyToCommit()) {
            return BrokerClusterDecision.rejected(operation,
                    new DecisionReason("membership lifecycle requires permit completion"));
        }
        /* 2. 故障恢复的验证与完成必须核对当前证据，不能用通用阶段推进绕过。 */
        if (operation.type() == BrokerClusterOperationType.FAIL_BROKER
                && (nextPhase == BrokerClusterOperationPhase.VERIFYING
                || nextPhase == BrokerClusterOperationPhase.SUCCEEDED
                || nextPhase == BrokerClusterOperationPhase.DEGRADED)) {
            return BrokerClusterDecision.rejected(operation,
                    new DecisionReason("route recovery requires verification or completion behavior"));
        }
        /* 3. 专用协议守卫优先于重复识别，随后核对调用方预期阶段。 */
        if (operation.phase() == nextPhase) {
            return BrokerClusterDecision.duplicate(operation, new DecisionReason("operation phase was already committed"));
        }
        if (operation.phase() != expectedPhase) {
            return BrokerClusterDecision.conflict(operation, new DecisionReason("operation phase does not match expected phase"));
        }
        /* 4. 先构造合法下一阶段，再保存；非法迁移映射为领域拒绝。 */
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

    /**
     * 为正在隔离旧 epoch 的操作登记 fence，隔离集合必须匹配当前 ACTIVE 节点。
     *
     * <p>同一事实返回重复；同操作、同代次和同集合仅允许以更高任期、
     * 更高 quorum ReadIndex 接续，不能用较旧事实覆盖已登记隔离。</p>
     */
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
        /* 身份、代次与集合校验通过后，区分首次登记、重复及换届接续。 */
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

    /**
     * 更新路由重建或验证阶段的排空进度，并在完成条件满足时进入 VERIFYING。
     *
     * <p>故障恢复汇总必须匹配当前 Gateway 检查点；重复更新返回重复结果，
     * 无效证据或进度迁移返回拒绝，不保存候选进度。</p>
     */
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

    /**
     * 在日志应用边界核验当前恢复证据、ACK 和期限，登记正常或降级完成意图。
     */
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

    /**
     * 依据聚合当前持有的恢复意图选择终态，重复完成不修改已提交事实。
     */
    public BrokerClusterDecision completeRouteRecovery(ClusterOperationId operationId, CommandDigest commandDigest, Instant changedAt) {
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

    /**
     * 完成已满足证据与期限要求的优雅排空，将 DRAINING 节点置为 INACTIVE。
     * 正常完成须处于 VERIFYING；首次接受时同时推进操作终态与聚合 revision。
     */
    public BrokerClusterDecision completeDrain(ClusterOperationId operationId,
                                               CommandDigest commandDigest,
                                               ClusterRevision expectedRevision,
                                               Instant changedAt) {
        return finishDrain(operationId, commandDigest, expectedRevision, null,
                changedAt, false);
    }

    /**
     * 依据显式原因强制结束 FAILED 排空，将仍为 DRAINING 的节点置为 INACTIVE。
     * 不要求正常完成证据，但仍核对操作身份、当前 revision 和目标节点状态。
     */
    public BrokerClusterDecision forceDrain(ClusterOperationId operationId,
                                            CommandDigest commandDigest,
                                            ClusterRevision expectedRevision,
                                            OperationFailureReason reason,
                                            Instant changedAt) {
        return finishDrain(operationId, commandDigest, expectedRevision, reason,
                changedAt, true);
    }

    /**
     * 从已提交节点与绑定端点冻结完整目标，角色转换必须先移除旧角色。
     */
    private List<BrokerMembershipStep> membershipStepsFor(BrokerClusterOperationProposal proposal) {
        ConsensusMembership expected = expectedMembership();
        Set<MembershipEndpoint> voters = new HashSet<>(expected.voters());
        Set<MembershipEndpoint> learners = new HashSet<>(expected.learners());
        MembershipEndpoint target = proposal.learnerJoin()
                ? proposal.membershipEndpoint()
                : membershipEndpoints.get(proposal.targetBrokerId());
        if (target == null) {
            throw new IllegalStateException("target membership binding is missing");
        }
        List<BrokerMembershipStep> steps = new ArrayList<>();
        switch (proposal.operationType()) {
            case ADD_LEARNER, START_LEARNER_SYNC -> learners.add(target);
            case REMOVE_BROKER -> {
                voters.remove(target);
                learners.remove(target);
            }
            case PROMOTE_LEARNER, PROMOTE_BROKER -> {
                learners.remove(target);
                steps.add(new BrokerMembershipStep(REMOVE_OLD_ROLE_STEP,
                        new ConsensusMembership(voters, learners), List.of()));
                voters.add(target);
            }
            case DEMOTE_VOTER, DEMOTE_BROKER -> {
                voters.remove(target);
                steps.add(new BrokerMembershipStep(REMOVE_OLD_ROLE_STEP,
                        new ConsensusMembership(voters, learners), List.of()));
                learners.add(target);
            }
            default -> throw new IllegalArgumentException("operation does not change native membership");
        }
        steps.add(new BrokerMembershipStep(FINAL_TARGET_STEP,
                new ConsensusMembership(voters, learners), List.of()));
        return List.copyOf(steps);
    }

    /**
     * 用新的许可事实替换步骤，并递增聚合 revision 形成可重放状态。
     */
    private BrokerClusterDecision replaceMembershipStep(BrokerClusterOperation operation, BrokerMembershipStep replacement) {
        List<BrokerMembershipStep> steps = operation.membershipSteps().stream()
                .map(step -> step.stepId().equals(replacement.stepId()) ? replacement : step).toList();
        BrokerClusterOperation updated = operation.withMembershipSteps(steps);
        ClusterRevision nextRevision = revision.next();
        operations.put(updated.id(), updated);
        revision = nextRevision;
        return BrokerClusterDecision.applied(updated);
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
        if (hasBlockingOperation()) {
            return Optional.of(BrokerClusterDecision.conflict(null,
                    new DecisionReason("another cluster change is awaiting topology commit or authorization")));
        }

        // 4. 校验节点资格、流量状态和最低 voter 数量；这一阶段不修改节点。
        Optional<DecisionReason> violation = checkOperationConstraints(
                proposal.operationType(), proposal.targetBrokerId(),
                proposal.routeEndpoint(), proposal.minimumVoterCount());
        return violation.map(decisionReason -> BrokerClusterDecision.rejected(null, decisionReason));
    }

    /**
     * 共用正常与强制排空的终态写入边界；校验和 revision 计算先于节点变更。
     */
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
        /* 1. 分别核对正常完成与强制完成的阶段要求，重复终态直接返回。 */
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

        /* 2. 节点必须仍在排空；操作版本匹配也不能代替节点状态检查。 */
        BrokerNode target = brokers.stream()
                .filter(node -> node.id().equals(existing.targetBrokerId()))
                .findFirst().orElse(null);
        if (target == null || target.trafficState() != BrokerTrafficState.DRAINING) {
            return BrokerClusterDecision.rejected(existing,
                    new DecisionReason("drain target must be DRAINING"));
        }
        try {
            /* 3. 先构造终态并计算下一 revision，再修改节点并保存操作。 */
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
}
