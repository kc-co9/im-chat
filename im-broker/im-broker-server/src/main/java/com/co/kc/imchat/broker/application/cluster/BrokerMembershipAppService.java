package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;

import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRemovalIntent;

import com.co.kc.imchat.broker.domain.cluster.model.VoterDemotionIntent;

import com.co.kc.imchat.broker.domain.cluster.model.LearnerPromotionIntent;

import com.co.kc.imchat.broker.domain.cluster.model.LearnerSyncCompletionIntent;

import com.co.kc.imchat.broker.domain.cluster.model.LearnerJoinIntent;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.PromoteLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.RemoveBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.result.AddLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DemoteVoterResult;
import com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult;
import com.co.kc.imchat.broker.model.cqrs.result.PromoteLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.RemoveBrokerResult;
import com.co.kc.imchat.broker.transformer.application.BrokerClusterAppTransformer;

import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 编排 Broker Raft 成员关系变更用例。
 *
 * <p>本服务负责把接口层的基础类型命令转换为领域值对象，读取已提交的
 * {@link BrokerCluster} 聚合，并按 Begin、外部成员变更、Commit 的顺序推进成员操作。
 * 它不直接修改聚合内部状态，也不实现 Raft 成员配置；共识提交和成员配置由
 * {@link BrokerClusterAdapter} 执行。</p>
 *
 * <p>成员变更通过 {@link ClusterOperationId} 保证幂等。新请求先校验当前 epoch，已登记操作
 * 则根据操作摘要和阶段恢复；对于可能发生外部部分提交的变更，未知结果保留为待恢复状态。</p>
 */
public final class BrokerMembershipAppService {
    private final BrokerClusterAdapter brokerClusterAdapter;
    private final MinimumVoterCount minimumVoterCount;

    /**
     * 创建成员关系用例编排器。
     *
     * @param brokerClusterAdapter 集群共识、成员配置和已提交状态适配器
     * @param minimumVoterCount    成员变更必须保留的最少 voter 数
     */
    public BrokerMembershipAppService(
            BrokerClusterAdapter brokerClusterAdapter,
            MinimumVoterCount minimumVoterCount) {
        this.brokerClusterAdapter = brokerClusterAdapter;
        this.minimumVoterCount = minimumVoterCount;
    }

    /**
     * 登记并执行一次 learner 加入操作。
     *
     * <p>Begin 成功后才调用 Raft 成员配置；成员配置明确接受时提交聚合状态，明确拒绝且
     * 可以证明没有外部部分提交时记录失败。未知结果保留操作为待恢复状态。</p>
     *
     * @param command 包含目标 Broker、成员端点、期望 epoch 和幂等标识的基础类型命令
     * @return learner 加入结果，不代表 learner 已完成日志同步
     */
    public CompletionStage<AddLearnerResult> addLearner(AddLearnerCommand command) {
        // 1. 将请求身份、端点和幂等摘要转换为领域值。
        BrokerId brokerId = new BrokerId(command.brokerId());
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        MembershipEndpoint membershipEndpoint = new MembershipEndpoint(command.membershipEndpoint());
        CommandDigest commandDigest = command.digest();

        // 2. 读取已提交聚合；新请求校验 epoch，已有操作由聚合核对幂等身份。
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
        if (!cluster.exists(operationId) && !cluster.epoch().equals(expectedEpoch)) {
            return CompletableFuture.completedFuture(AddLearnerResult.conflict(operationId));
        }

        // 3. 由聚合检查准入并冻结操作意图，不直接改变 Raft 成员配置。
        LearnerJoinIntent intent = LearnerJoinIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .membershipEndpoint(membershipEndpoint)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.addLearner(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(AddLearnerResult.failure(operationId, decision));
        }

        // 4. 先持久化操作意图，只有仍处于 PENDING 的操作才能执行成员变更。
        return brokerClusterAdapter.registerOperation(decision.operation())
                .thenCompose(consensusResult -> {
                    if (!consensusResult.hasPendingOperation()) {
                        return CompletableFuture.completedFuture(consensusResult);
                    }

                    // 5. 添加 learner，再根据成员提交结果提交拓扑或记录失败。
                    return brokerClusterAdapter.addLearner(membershipEndpoint)
                            .thenCompose(membershipResult ->
                                    handleMembershipResult(consensusResult.operation(), membershipResult));
                })
                // 6. 收口已提交操作，再转换为应用结果；重试不重复执行外部变更。
                .thenCompose(this::completeMembershipOperation)
                .thenApply(consensusResult ->
                        BrokerClusterAppTransformer.INSTANCE.addLearnerResultFrom(operationId, consensusResult));
    }

    /**
     * 确认 learner 已应用到 quorum ReadIndex，并提交其 READY 状态。
     *
     * <p>新操作会先确认目标仍属于已提交的 learner 配置，再比较 Leader 的 quorum ReadIndex
     * 与目标节点的应用位置；历史重试沿用已登记操作，不重复读取同步证据。</p>
     *
     * @param command 包含目标 Broker、成员端点、期望 epoch 和幂等标识的基础类型命令
     * @return 同步确认结果；证据不足、不是 Leader 或共识不可用时返回对应状态
     */
    public CompletionStage<CompleteLearnerSyncResult> confirmLearner(ConfirmLearnerCommand command) {
        // 1. 转换目标身份、端点、epoch 和操作摘要。
        BrokerId brokerId = new BrokerId(command.brokerId());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        MembershipEndpoint membershipEndpoint = new MembershipEndpoint(command.membershipEndpoint());
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());
        CommandDigest commandDigest = command.digest();

        // 2. 读取已提交聚合，拒绝基于过期 epoch 的新操作。
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
        if (!cluster.exists(operationId)) {
            if (!cluster.epoch().equals(expectedEpoch)) {
                return CompletableFuture.completedFuture(CompleteLearnerSyncResult.conflict(operationId, cluster.epoch()));
            }
            if (!cluster.hasMembershipEndpoint(brokerId, membershipEndpoint)) {
                return CompletableFuture.completedFuture(CompleteLearnerSyncResult.rejected(operationId, cluster.epoch()));
            }
        }

        // 3. 先在独立聚合上检查资格和幂等身份；此时尚未提交 Begin。
        LearnerSyncCompletionIntent intent = LearnerSyncCompletionIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.confirmLearner(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(CompleteLearnerSyncResult.failure(operationId, cluster.epoch(), decision));
        }

        // 4. 按领域决定衔接异步流程；历史重试不重新读取同步证据。
        return CompletableFuture.completedFuture(decision)
                .thenCompose(acceptedDecision -> {
                    if (acceptedDecision.duplicate()) {
                        return brokerClusterAdapter.registerOperation(acceptedDecision.operation());
                    }
                    // 新操作取得同步证据后才能提交 Begin。
                    return checkLearnerProgress(cluster.findNode(brokerId).orElseThrow(), membershipEndpoint)
                            .thenCompose(progress -> {
                                if (!progress.accepted()) {
                                    return CompletableFuture.completedFuture(progress);
                                }
                                return brokerClusterAdapter.registerOperation(acceptedDecision.operation());
                            });
                })
                .thenCompose(begin -> {
                    if (!begin.hasPendingOperation()) {
                        return CompletableFuture.completedFuture(begin);
                    }
                    return brokerClusterAdapter.commitOperation(begin.operation(), Instant.now());
                })
                // 5. 提交节点状态后推进操作终态，并返回同步完成结果。
                .thenCompose(this::completeMembershipOperation)
                .thenApply(consensusResult ->
                        BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncResultFrom(operationId, cluster.epoch(), consensusResult));
    }


    /**
     * 登记并执行 learner 晋升为 voter 的成员变更。
     *
     * <p>晋升可能在 Raft 成员配置变更后、Broker 聚合提交前中断；未知结果和部分提交不会
     * 被强制标记为失败，而是保留 operation 供恢复流程继续处理。</p>
     *
     * @param command 包含目标 Broker、成员端点、期望 epoch 和幂等标识的基础类型命令
     * @return voter 晋升结果及当前已知的 Leader 信息
     */
    public CompletionStage<PromoteLearnerResult> promoteLearner(PromoteLearnerCommand command) {
        // 1. 将请求转换为领域身份、端点、epoch 和幂等摘要。
        BrokerId brokerId = new BrokerId(command.brokerId());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        MembershipEndpoint membershipEndpoint = new MembershipEndpoint(command.membershipEndpoint());
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());
        CommandDigest commandDigest = command.digest();

        // 2. 读取已提交聚合，已有操作允许按原请求恢复，新操作必须匹配 epoch。
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
        if (!cluster.exists(operationId)) {
            if (!cluster.epoch().equals(expectedEpoch)) {
                return CompletableFuture.completedFuture(PromoteLearnerResult.conflict(
                        operationId, cluster.epoch()));
            }
            if (!cluster.hasMembershipEndpoint(brokerId, membershipEndpoint)) {
                return CompletableFuture.completedFuture(PromoteLearnerResult.rejected(
                        operationId, cluster.epoch()));
            }
        }

        // 3. 先登记晋升意图；只有 PENDING 操作继续修改 Raft 成员。
        LearnerPromotionIntent intent = LearnerPromotionIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.promoteLearner(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(PromoteLearnerResult.failure(operationId, cluster.epoch(), decision));
        }

        return brokerClusterAdapter.registerOperation(decision.operation())
                .thenCompose(begin -> {
                    if (!begin.hasPendingOperation()) {
                        return CompletableFuture.completedFuture(begin);
                    }

                    // 4. 晋升包含两步配置变更，拒绝时可能部分提交，保留操作等待核对。
                    return brokerClusterAdapter.promoteLearner(membershipEndpoint)
                            .thenCompose(membership ->
                                    handleMembershipResult(begin.operation(), membership));
                })
                // 5. 完成拓扑提交后的终态收口，再转换应用结果。
                .thenCompose(this::completeMembershipOperation)
                .thenApply(result ->
                        BrokerClusterAppTransformer.INSTANCE.promoteLearnerResultFrom(operationId, cluster.epoch(), result));
    }

    /**
     * 登记并执行 voter 降级为 learner 的成员变更。
     *
     * <p>领域聚合负责校验目标状态和最小 voter 数量，本服务负责提交 operation、调用 Raft
     * 成员配置并在结果明确后推进聚合阶段。</p>
     *
     * @param command 包含目标 Broker、成员端点、期望 epoch 和幂等标识的基础类型命令
     * @return voter 降级结果
     */
    public CompletionStage<DemoteVoterResult> demoteVoter(DemoteVoterCommand command) {
        // 1. 转换请求和最小 voter 数约束，交由领域聚合判断成员安全性。
        BrokerId brokerId = new BrokerId(command.brokerId());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        MembershipEndpoint membershipEndpoint = new MembershipEndpoint(command.membershipEndpoint());
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());
        CommandDigest commandDigest = command.digest();

        // 2. 读取已提交聚合，只对新操作检查当前 epoch。
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
        if (!cluster.exists(operationId)) {
            if (!cluster.epoch().equals(expectedEpoch)) {
                return CompletableFuture.completedFuture(DemoteVoterResult.conflict(operationId, cluster.epoch()));
            }
            if (!cluster.hasMembershipEndpoint(brokerId, membershipEndpoint)) {
                return CompletableFuture.completedFuture(DemoteVoterResult.rejected(operationId, cluster.epoch()));
            }
        }

        // 3. 由聚合校验并登记降级意图，拒绝或已完成的操作不执行外部变更。
        VoterDemotionIntent intent = VoterDemotionIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .minimumVoterCount(minimumVoterCount)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.demoteVoter(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(DemoteVoterResult.failure(operationId, cluster.epoch(), decision));
        }

        return brokerClusterAdapter.registerOperation(decision.operation())
                .thenCompose(begin -> {
                    if (!begin.hasPendingOperation()) {
                        return CompletableFuture.completedFuture(begin);
                    }

                    // 4. 执行两步降级；不确定或部分提交时保留 PENDING 供恢复。
                    return brokerClusterAdapter.demoteVoter(membershipEndpoint)
                            .thenCompose(membership -> handleMembershipResult(begin.operation(), membership));
                })
                // 5. 拓扑提交后记录成功终态，再返回降级结果。
                .thenCompose(this::completeMembershipOperation)
                .thenApply(result -> BrokerClusterAppTransformer.INSTANCE.demoteVoterResultFrom(
                        operationId, cluster.epoch(), result));
    }

    /**
     * 从 Raft 成员关系和 Broker 拓扑中移除已经 INACTIVE 的节点。
     *
     * <p>Raft 成员移除明确提交后，才提交 Broker 拓扑；未知结果保留待恢复，避免覆盖外部
     * 成员关系的不确定状态。</p>
     *
     * @param command 包含目标 Broker、成员端点、期望 epoch 和幂等标识的基础类型命令
     * @return Broker 移除结果
     */
    public CompletionStage<RemoveBrokerResult> removeBroker(RemoveBrokerCommand command) {
        // 1. 转换目标、端点与操作身份，并构造最小 voter 数约束。
        BrokerId brokerId = new BrokerId(command.brokerId());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        MembershipEndpoint membershipEndpoint = new MembershipEndpoint(command.membershipEndpoint());
        CommandDigest commandDigest = command.digest();
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());

        // 2. 读取已提交聚合；重试允许目标已移除，新请求仍须匹配 epoch。
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
        if (!cluster.exists(operationId)) {
            if (!cluster.epoch().equals(expectedEpoch)) {
                return CompletableFuture.completedFuture(RemoveBrokerResult.conflict(
                        operationId, cluster.epoch()));
            }
            if (!cluster.hasMembershipEndpoint(brokerId, membershipEndpoint)) {
                return CompletableFuture.completedFuture(RemoveBrokerResult.rejected(
                        operationId, cluster.epoch()));
            }
        }

        // 3. 先登记移除意图，避免外部成员已移除却没有可恢复的操作记录。
        BrokerRemovalIntent intent = BrokerRemovalIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .minimumVoterCount(minimumVoterCount)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.removeBroker(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(RemoveBrokerResult.failure(operationId, cluster.epoch(), decision));
        }

        return brokerClusterAdapter.registerOperation(decision.operation())
                .thenCompose(begin -> {
                    if (!begin.hasPendingOperation()) {
                        return CompletableFuture.completedFuture(begin);
                    }

                    // 4. 从共识配置移除成员，再提交已登记操作以修改内部 Broker 节点。
                    return brokerClusterAdapter.removeMember(membershipEndpoint)
                            .thenCompose(membership -> handleMembershipResult(begin.operation(), membership));
                })
                // 5. 收口成功操作，转换为移除结果。
                .thenCompose(this::completeMembershipOperation)
                .thenApply(result -> BrokerClusterAppTransformer.INSTANCE.removeBrokerResultFrom(
                        operationId, cluster.epoch(), result));
    }

    /**
     * 根据已提交 operation 恢复中断的成员变更。
     *
     * <p>目标成员端点必须从聚合已登记的绑定中读取，不能由恢复调用方重新构造。已提交但未
     * 完成的操作只推进终态；仍处于 PENDING 的操作按原类型重新进入对应应用用例。</p>
     *
     * @param operationId 已登记的成员变更操作标识
     * @return 对应成员用例的异步结果，或恢复失败信息
     */
    public CompletionStage<?> resume(ClusterOperationId operationId) {
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
        BrokerClusterOperation operation = cluster.findOperation(operationId)
                .orElseThrow(() -> new IllegalArgumentException("operation does not exist"));
        if (operation.phase() == BrokerClusterOperationPhase.RAFT_COMMITTED) {
            return completeMembershipOperation(BrokerClusterConsensusResult.applied(operation));
        }
        if (operation.phase() != BrokerClusterOperationPhase.PENDING) {
            return CompletableFuture.failedFuture(new IllegalStateException("membership operation is not pending"));
        }

        MembershipEndpoint endpoint = cluster.membershipEndpoints().get(operation.targetBrokerId());
        if (endpoint == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("pending membership operation has no committed endpoint binding"));
        }

        return switch (operation.type()) {
            case START_LEARNER_SYNC -> addLearner(
                    BrokerClusterAppTransformer.INSTANCE.addLearnerCommandFrom(operation, endpoint));
            case COMPLETE_LEARNER_SYNC -> confirmLearner(
                    BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncCommandFrom(operation, endpoint));
            case PROMOTE_LEARNER -> promoteLearner(
                    BrokerClusterAppTransformer.INSTANCE.promoteLearnerCommandFrom(operation, endpoint));
            case DEMOTE_VOTER -> demoteVoter(
                    BrokerClusterAppTransformer.INSTANCE.demoteVoterCommandFrom(operation, endpoint));
            case REMOVE_BROKER -> removeBroker(
                    BrokerClusterAppTransformer.INSTANCE.removeBrokerCommandFrom(operation, endpoint));
            default ->
                    CompletableFuture.failedFuture(new IllegalArgumentException("operation is not a membership change"));
        };
    }

    /**
     * 获取 learner 的共识成员身份及日志进度证据。
     *
     * <p>只读取已提交成员配置、Leader quorum ReadIndex 和目标节点已应用位置，不登记
     * operation，也不修改集群拓扑。</p>
     *
     * @param learner            目标 learner 的领域节点
     * @param membershipEndpoint 目标节点在 Raft 成员配置中的端点
     * @return 证据检查结果；APPLIED 表示可以继续登记同步确认操作
     */
    private CompletionStage<BrokerClusterConsensusResult> checkLearnerProgress(
            BrokerNode learner, MembershipEndpoint membershipEndpoint) {
        // 1. 本机 Raft 节点不是集群 Leader，返回检查失败及已知 Leader 地址。
        if (!brokerClusterAdapter.getLocalBroker().isLeader()) {
            return CompletableFuture.completedFuture(BrokerClusterConsensusResult.notLeader(brokerClusterAdapter.getLocalBroker().leaderEndpoint(), "learner progress check requires the cluster leader"));
        }
        // 2. 确认 learner 已进入共识配置，再比较 quorum ReadIndex 与节点应用位置。
        if (!brokerClusterAdapter.consensusMembership().learners().contains(membershipEndpoint.value())) {
            return CompletableFuture.completedFuture(BrokerClusterConsensusResult.rejected("learner has not joined the committed consensus membership"));
        }
        return brokerClusterAdapter.confirmLeadership(new QuorumReadNonce(GeneratorUtils.nextUUID()))
                .thenCompose(read -> brokerClusterAdapter.fetchQuorumReadIndex(learner)
                        .thenApply(index -> {
                            if (index >= read.readIndex().value()) {
                                return BrokerClusterConsensusResult.applied();
                            } else {
                                return BrokerClusterConsensusResult.rejected("learner has not applied the committed Raft read index");
                            }
                        })
                        // 3. 证据获取失败不登记操作；区分失去领导权与当前 Leader 暂不可用。
                        .exceptionally(failure -> {
                            if (failure instanceof CompletionException && failure.getCause() != null) {
                                failure = failure.getCause();
                            }
                            if (brokerClusterAdapter.getLocalBroker().isLeader()) {
                                return BrokerClusterConsensusResult.unavailable(brokerClusterAdapter.getLocalBroker().leaderEndpoint(), failure.getMessage());
                            } else {
                                return BrokerClusterConsensusResult.notLeader(brokerClusterAdapter.getLocalBroker().leaderEndpoint(), failure.getMessage());
                            }
                        })
                );
    }

    /**
     * 处理 Raft 成员配置结果。
     *
     * <p>成员配置已接受时提交 Broker 聚合；单步操作收到明确拒绝时才记录 FAILED。两步
     * 操作的拒绝或未知结果可能包含外部部分提交，因此保留 PENDING 等待恢复核对。</p>
     *
     * @param operation        已登记的成员变更操作
     * @param membershipResult Raft 成员配置返回结果
     * @return 后续聚合提交、失败记录或待恢复结果
     */
    private CompletionStage<BrokerClusterConsensusResult> handleMembershipResult(
            BrokerClusterOperation operation, BrokerConsensusMembershipResult membershipResult) {
        // 1. 成员配置已确认提交，继续提交同一操作以修改聚合内部节点。
        if (membershipResult.accepted()) {
            return brokerClusterAdapter.commitOperation(operation, Instant.now());
        }
        // 2. 保留成员拒绝/未知结果；两步变更不能假设没有外部部分提交。
        BrokerClusterConsensusResult failure =
                BrokerClusterAppTransformer.INSTANCE.consensusResultFrom(membershipResult);
        if (!operation.canFailOnRejection() || !membershipResult.rejected()) {
            return CompletableFuture.completedFuture(failure);
        }
        // 3. 单步明确拒绝才写 FAILED；记录失败阶段未成功时返回共识失败。
        AdvanceClusterOperationCommand failureCommand =
                BrokerClusterAppTransformer.INSTANCE.membershipFailureCommandFrom(operation, membershipResult, Instant.now());
        return brokerClusterAdapter.submitChange(failureCommand).thenApply(consensusResult -> {
            if (!consensusResult.accepted()) {
                return consensusResult;
            }
            return failure;
        });
    }

    /**
     * 将已提交成员拓扑的 operation 推进到 SUCCEEDED。
     *
     * <p>只有操作处于已提交、尚未完成的阶段时才写入终态；失败、未提交或已经进入终态的
     * 结果直接返回，避免重复写入完成命令。</p>
     *
     * @param result 成员变更的共识处理结果
     * @return 终态提交结果或原结果
     */
    private CompletionStage<BrokerClusterConsensusResult> completeMembershipOperation(
            BrokerClusterConsensusResult result) {
        // 1. 失败、尚未提交或已进入终态的操作直接返回，不重复推进阶段。
        if (!result.hasCommittedOperationToComplete()) {
            return CompletableFuture.completedFuture(result);
        } else {
            // 2. 持久化成功终态，创建与完成时间统一为毫秒精度，允许同毫秒完成。
            return brokerClusterAdapter.submitChange(
                    BrokerClusterAppTransformer.INSTANCE.membershipCompletionCommandFrom(result.operation(), Instant.now()));
        }
    }
}
