package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor;
import com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipStep;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRemovalIntent;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationAttempt;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationClaim;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationIdentity;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerJoinIntent;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerPromotionIntent;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerSyncCompletionIntent;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionAdmission;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationAttempt;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationClaim.Status;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationClaim;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.VoterDemotionIntent;
import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.broker.model.cqrs.command.PromoteLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand;
import com.co.kc.imchat.broker.model.cqrs.command.RemoveBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.result.AddLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult;
import com.co.kc.imchat.broker.model.cqrs.result.DemoteVoterResult;
import com.co.kc.imchat.broker.model.cqrs.result.PromoteLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.RemoveBrokerResult;
import com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult;
import com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer;
import com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer;
import com.co.kc.imchat.broker.transformer.application.BrokerClusterAppTransformer;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.common.utils.GeneratorUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import lombok.extern.slf4j.Slf4j;

/**
 * 编排 Broker Raft 成员关系变更用例。
 *
 * <p>
 * 本服务负责把接口层的基础类型命令转换为领域值对象，读取已提交的
 * {@link BrokerCluster} 聚合，并按 Begin、外部成员变更、Commit 的顺序推进成员操作。
 * 它不直接修改聚合内部状态，也不实现 Raft 成员配置；共识提交和成员配置由
 * {@link BrokerRaftAdapter} 执行。
 * </p>
 *
 * <p>
 * 成员变更通过 {@link ClusterOperationId} 保证幂等。新请求先校验当前 epoch，已登记操作
 * 则根据操作摘要和阶段恢复；对于可能发生外部部分提交的变更，未知结果保留为待恢复状态。
 * </p>
 *
 * <p>
 * 阅读成员变更主流程时，按以下顺序阅读：
 * {@link #submitMembershipOperation(BrokerClusterOperation)} 负责进入受控执行器；
 * {@link #processMembershipOperation(MembershipOperationAttempt, Executor)} 负责
 * Leader、幂等和 Begin；
 * {@link #advanceMembershipOperation(BrokerClusterConsensusResult, Optional, Executor)}
 * 负责按步骤
 * 登记或恢复执行许可、执行成员变更、确认执行结果并提交操作；
 * {@link #confirmExecution(AcceptMembershipPermitDrainedCommand, Executor)}
 * 取得有效集群读取证据并提交执行结束的结果。add/promote/demote/remove 方法只负责
 * 将外部 Command 转成领域 intent，learner 确认是独立的同步证据流程。
 * </p>
 */
@Slf4j
public final class BrokerMembershipAppService {
    /**
     * 负责成员操作提交、恢复以及许可确认的 Raft 适配器。
     */
    private final BrokerRaftAdapter brokerRaftAdapter;
    /**
     * 负责跨 Broker 发送已排空许可事实的对端适配器。
     */
    private final BrokerPeerAdapter brokerPeerAdapter;
    /**
     * 变更后必须保留的最小 VOTER 数量。
     */
    private final MinimumVoterCount minimumVoterCount;
    /**
     * 串行执行成员许可并管理本地排空事实的执行器。
     */
    private final BrokerMembershipExecutor permitExecutor;
    /**
     * 管理成员请求认领、身份保留及 learner 确认占用，不负责线程调度。
     */
    private final MembershipOperationService membershipOperationService;
    /**
     * 记录成员配置诊断的最近采样并提供监控指标，不参与成员变更准入判断。
     */
    private final MembershipDiagnosticMetrics diagnosticMetrics;

    /**
     * 创建成员关系用例编排器。
     *
     * @param brokerRaftAdapter 集群共识、成员配置和已提交状态适配器
     * @param minimumVoterCount 成员变更必须保留的最少 voter 数
     */
    public BrokerMembershipAppService(
            BrokerRaftAdapter brokerRaftAdapter,
            BrokerPeerAdapter brokerPeerAdapter,
            MinimumVoterCount minimumVoterCount,
            BrokerMembershipExecutor permitExecutor,
            MembershipDiagnosticMetrics diagnosticMetrics,
            MembershipOperationService membershipOperationService) {
        this.brokerRaftAdapter = brokerRaftAdapter;
        this.brokerPeerAdapter = brokerPeerAdapter;
        this.minimumVoterCount = minimumVoterCount;
        this.permitExecutor = permitExecutor;
        this.diagnosticMetrics = diagnosticMetrics;
        this.membershipOperationService = membershipOperationService;
    }

    /**
     * 登记并执行一次 learner 加入操作。
     *
     * <p>
     * Begin 成功后才调用 Raft 成员配置；成员配置明确接受时提交聚合状态，明确拒绝且
     * 可以证明没有外部部分提交时记录失败。未知结果保留操作为待恢复状态。
     * </p>
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
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
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
        return submitMembershipOperation(decision.operation())
                .thenApply(consensusResult ->
                        BrokerClusterAppTransformer.INSTANCE.addLearnerResultFrom(operationId, consensusResult));
    }

    /**
     * 确认 learner 已应用到 quorum ReadIndex，并提交其 READY 状态。
     *
     * <p>
     * 新操作会先确认目标仍属于已提交的 learner 配置，再比较 Leader 的 quorum ReadIndex
     * 与目标节点的应用位置；历史重试沿用已登记操作，不重复读取同步证据。
     * </p>
     *
     * @param command 包含目标 Broker、成员端点、期望 epoch 和幂等标识的基础类型命令
     * @return 同步确认结果；证据不足、不是 Leader 或共识不可用时返回对应状态
     */
    public CompletionStage<CompleteLearnerSyncResult> confirmLearner(ConfirmLearnerCommand command) {
        // 1. 转换领域身份、端点、epoch 和请求摘要。
        BrokerId brokerId = new BrokerId(command.brokerId());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        MembershipEndpoint membershipEndpoint = new MembershipEndpoint(command.membershipEndpoint());
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());
        CommandDigest commandDigest = command.digest();

        // 2. 原子认领在途请求；相同重复立即报告处理中，不共享执行者结果。
        LearnerConfirmationIdentity identity = new LearnerConfirmationIdentity(operationId, brokerId, expectedEpoch, membershipEndpoint);
        LearnerConfirmationClaim claim = membershipOperationService.claimLearnerConfirmation(identity);
        switch (claim.status()) {
            case IN_PROGRESS:
                return CompletableFuture.completedStage(CompleteLearnerSyncResult.inProgress(operationId));
            case CONFLICT:
                return CompletableFuture.completedStage(CompleteLearnerSyncResult.inFlightConflict(operationId, expectedEpoch));
            case ACQUIRED:
                break;
        }

        LearnerConfirmationAttempt executionAttempt = claim.executionAttempt();
        CompletableFuture<CompleteLearnerSyncResult> completion = new CompletableFuture<>();
        CompletionStage<CompleteLearnerSyncResult> resultView = completion.minimalCompletionStage();
        Executor execution = permitExecutor.reserveLearnerExecution(() -> {
            // 本轮执行资格已失效：释放本次占用，并通知调用方不能继续。
            // 由 worker 消费失效阶段时调用，不读取 Node 或已提交状态。
            CompleteLearnerSyncResult result = CompleteLearnerSyncResult.notLeader(
                    operationId, expectedEpoch, "learner workflow role generation is no longer current");
            membershipOperationService.releaseLearnerConfirmation(executionAttempt);
            completion.complete(result);
        });
        if (execution == null) {
            membershipOperationService.releaseLearnerConfirmation(executionAttempt);
            return CompletableFuture.completedStage(CompleteLearnerSyncResult.unavailable(
                    operationId, expectedEpoch, "learner workflow outcome is unknown"));
        }

        // 3. 准备事实；历史请求直接结束，不进入新操作副作用。
        return CompletableFuture.<LearnerConfirmationState>supplyAsync(() -> {
                    Optional<LeaderTerm> expectedTerm = brokerRaftAdapter.getLocalBroker().getLeaderTerm();
                    // 读取已提交聚合，拒绝基于过期 epoch 的新操作。
                    BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
                    if (!cluster.exists(operationId)) {
                        if (!cluster.epoch().equals(expectedEpoch)) {
                            return new LearnerConfirmationState.Finished(
                                    CompleteLearnerSyncResult.conflict(operationId, cluster.epoch()));
                        }
                        if (!cluster.hasMembershipEndpoint(brokerId, membershipEndpoint)) {
                            return new LearnerConfirmationState.Finished(
                                    CompleteLearnerSyncResult.rejected(operationId, cluster.epoch()));
                        }
                    }

                    // 先在独立聚合上检查资格和幂等身份；此时尚未提交 Begin。
                    LearnerSyncCompletionIntent intent = LearnerSyncCompletionIntent.builder()
                            .operationId(operationId)
                            .commandDigest(commandDigest)
                            .expectedRevision(cluster.revision())
                            .brokerId(brokerId)
                            .changedAt(Instant.now())
                            .build();
                    BrokerClusterDecision decision = cluster.confirmLearner(intent);
                    if (!decision.accepted()) {
                        return new LearnerConfirmationState.Finished(
                                CompleteLearnerSyncResult.failure(operationId, cluster.epoch(), decision));
                    }

                    if (decision.duplicate()) {
                        BrokerClusterConsensusResult repeated = BrokerClusterConsensusResult.duplicate(
                                decision.operation(),
                                "operation is already registered; query its state while background recovery completes");
                        return new LearnerConfirmationState.Finished(
                                BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncResultFrom(operationId, cluster.epoch(), repeated));
                    }
                    return new LearnerConfirmationState.Prepared(cluster, expectedTerm, intent, decision.operation());
                }, execution)
                // 4. 历史请求在此结束；只有新操作进入取证与提交链。
                .thenComposeAsync(state -> {
                    if (state instanceof LearnerConfirmationState.Finished(CompleteLearnerSyncResult result)) {
                        return CompletableFuture.completedStage(result);
                    }
                    LearnerConfirmationState.Prepared prepared = (LearnerConfirmationState.Prepared) state;
                    if (!matchesCurrentLeaderTerm(prepared.expectedTerm())) {
                        return CompletableFuture.completedStage(
                                BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncResultFrom(
                                        operationId, prepared.cluster().epoch(),
                                        BrokerClusterConsensusResult.notLeader(brokerRaftAdapter.getLeaderEndpoint(),
                                                "learner workflow Leader term changed")));
                    }
                    return verifyLearnerCaughtUp(prepared.cluster().findNode(brokerId).orElseThrow(),
                            membershipEndpoint, prepared.expectedTerm(), execution)
                            // 5. 等待取证后重读 committed 事实并复验任期，通过后才提交 Begin。
                            .thenComposeAsync(evidence -> {
                                if (!evidence.accepted()) {
                                    return CompletableFuture.completedStage(evidence);
                                }
                                BrokerCluster current = brokerRaftAdapter.getClusterSnapshot();
                                if (!current.revision().equals(prepared.intent().expectedRevision())
                                        || !current.epoch().equals(expectedEpoch)
                                        || !current.hasMembershipEndpoint(brokerId, membershipEndpoint)) {
                                    return CompletableFuture.completedStage(BrokerClusterConsensusResult.rejected(
                                            "learner evidence committed revision or endpoint changed"));
                                }
                                if (!matchesCurrentLeaderTerm(prepared.expectedTerm())) {
                                    return CompletableFuture.completedStage(BrokerClusterConsensusResult.notLeader(
                                            brokerRaftAdapter.getLeaderEndpoint(), "learner evidence Leader term changed"));
                                }
                                BrokerClusterConsensusResult admission = checkFreshMembershipAdmission(current, prepared.expectedTerm());
                                if (!admission.accepted()) {
                                    return CompletableFuture.completedStage(admission);
                                }
                                return brokerRaftAdapter.beginOperation(prepared.operation());
                            }, execution)
                            // 6. Begin 发现已有登记时不接管恢复；仅本次新登记继续 Commit。
                            .thenComposeAsync(registered -> {
                                if (registered.status() == BrokerClusterConsensusResult.Status.DUPLICATE
                                        || !registered.hasPendingOperation()) {
                                    return CompletableFuture.completedStage(registered);
                                }
                                if (!matchesCurrentLeaderTerm(prepared.expectedTerm())) {
                                    return CompletableFuture.completedStage(BrokerClusterConsensusResult.notLeader(
                                            brokerRaftAdapter.getLeaderEndpoint(), "learner commit Leader term changed"));
                                }
                                return brokerRaftAdapter.commitOperation(registered.operation());
                            }, execution)
                            // 7. 只有本次提交的结果进入终态完成；DUPLICATE 不补历史终态。
                            .thenComposeAsync(committed -> {
                                if (committed.status() == BrokerClusterConsensusResult.Status.DUPLICATE
                                        || !committed.hasCommittedOperationToComplete()) {
                                    return CompletableFuture.completedStage(committed);
                                }
                                if (!matchesCurrentLeaderTerm(prepared.expectedTerm())) {
                                    return CompletableFuture.completedStage(BrokerClusterConsensusResult.notLeader(
                                            brokerRaftAdapter.getLeaderEndpoint(), "learner completion Leader term changed"));
                                }
                                return brokerRaftAdapter.completeMembershipOperation(committed.operation());
                            }, execution)
                            .thenApplyAsync(completed -> BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncResultFrom(
                                    operationId, prepared.cluster().epoch(), completed), execution);
                }, execution)
                .handleAsync((result, failure) -> failure == null ? result : CompleteLearnerSyncResult.unavailable(
                        operationId, expectedEpoch, "learner workflow outcome is unknown"), execution)
                // 保留正常发布前的 generation 门禁；失效时由原回调完成共享桥梁。
                .thenApplyAsync(result -> {
                    membershipOperationService.releaseLearnerConfirmation(executionAttempt);
                    completion.complete(result);
                    return result;
                }, execution)
                .applyToEither(resultView, result -> result)
                .minimalCompletionStage();
    }

    /**
     * 登记并执行 learner 晋升为 voter 的成员变更。
     *
     * <p>
     * 晋升可能在 Raft 成员配置变更后、Broker 聚合提交前中断；未知结果和部分提交不会
     * 被强制标记为失败，而是保留 operation 供恢复流程继续处理。
     * </p>
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
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
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
            return CompletableFuture
                    .completedFuture(PromoteLearnerResult.failure(operationId, cluster.epoch(), decision));
        }

        return submitMembershipOperation(decision.operation())
                .thenApply(result -> BrokerClusterAppTransformer.INSTANCE.promoteLearnerResultFrom(operationId,
                        cluster.epoch(), result));
    }

    /**
     * 登记并执行 voter 降级为 learner 的成员变更。
     *
     * <p>
     * 领域聚合负责校验目标状态和最小 voter 数量，本服务负责提交 operation、调用 Raft
     * 成员配置并在结果明确后推进聚合阶段。
     * </p>
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
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
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

        return submitMembershipOperation(decision.operation())
                .thenApply(result -> BrokerClusterAppTransformer.INSTANCE.demoteVoterResultFrom(
                        operationId, cluster.epoch(), result));
    }

    /**
     * 从 Raft 成员关系和 Broker 拓扑中移除已经 INACTIVE 的节点。
     *
     * <p>
     * Raft 成员移除明确提交后，才提交 Broker 拓扑；未知结果保留待恢复，避免覆盖外部
     * 成员关系的不确定状态。
     * </p>
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
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
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
            return CompletableFuture
                    .completedFuture(RemoveBrokerResult.failure(operationId, cluster.epoch(), decision));
        }

        return submitMembershipOperation(decision.operation())
                .thenApply(result -> BrokerClusterAppTransformer.INSTANCE.removeBrokerResultFrom(
                        operationId, cluster.epoch(), result));
    }

    /**
     * 根据已提交 operation 恢复中断的成员变更。
     *
     * <p>
     * 目标成员端点必须从聚合已登记的绑定中读取，不能由恢复调用方重新构造。已提交但未
     * 完成的 learner 确认只推进终态；PENDING 确认走独立恢复链，原生成员变更走成员执行流程。
     * </p>
     *
     * @param operationId 已登记的成员变更操作标识
     * @return 对应成员用例的异步结果，或恢复失败信息
     */
    public CompletionStage<?> resume(ClusterOperationId operationId) {
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        BrokerClusterOperation operation = cluster.findOperation(operationId)
                .orElseThrow(() -> new IllegalArgumentException("operation does not exist"));
        if (operation.type() == BrokerClusterOperationType.COMPLETE_LEARNER_SYNC
                && (operation.phase() == BrokerClusterOperationPhase.PENDING
                || operation.phase() == BrokerClusterOperationPhase.RAFT_COMMITTED)) {
            MembershipEndpoint endpoint = cluster.membershipEndpoints().get(operation.targetBrokerId());
            if (endpoint == null) {
                return CompletableFuture
                        .failedFuture(new IllegalStateException("learner operation has no committed endpoint binding"));
            }
            return resumeLearner(BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncCommandFrom(operation, endpoint));
        }
        if (operation.nativeMembershipChange()) {
            return submitMembershipOperation(operation);
        }
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

        return CompletableFuture.failedFuture(new IllegalArgumentException("operation is not a membership change"));
    }

    /**
     * 接收并确认成员变更的执行结果，返回可衔接后续处理的异步结果。
     */
    public CompletionStage<MembershipConfirmationResult> confirmExecution(
            AcceptMembershipPermitDrainedCommand request) {
        return prepareExecutionConfirmation(request).stage();
    }

    /**
     * 接收执行结果确认，返回供调用方限时等待的句柄。
     * 本方法不阻塞，取消句柄不影响确认流程。
     */
    public Future<MembershipConfirmationResult> submitExecutionConfirmation(
            AcceptMembershipPermitDrainedCommand request) {
        return prepareExecutionConfirmation(request).waiting();
    }

    /**
     * 将本机已有的成员执行结果发送给指定 Broker 确认，不重新执行成员变更。
     */
    public CompletionStage<MembershipConfirmationResult> sendExecutionResult(
            ClusterOperationId operationId, BrokerId receiverBrokerId) {
        return permitExecutor.retransmitDrained(operationId, receiverBrokerId,
                () -> sendRecordedExecutionResult(operationId, receiverBrokerId));
    }

    /**
     * 调度一次只读成员配置诊断，不触发配置修复或自动接管。
     */
    public void diagnoseMembershipConfiguration() {
        permitExecutor.diagnoseMembership(this::inspectMembershipConfiguration);
    }

    /**
     * 返回当前是否有成员维护被未完成操作或 learner 同步状态阻塞。
     */
    public long membershipMaintenanceBlocked() {
        BrokerCluster snapshot = brokerRaftAdapter.getClusterSnapshot();
        if (snapshot.hasPendingMembershipOperation()) {
            return 1L;
        }
        if (snapshot.hasSyncingLearner()) {
            return 1L;
        }
        return permitExecutor.undrainedPermitCount() > 0 ? 1L : 0L;
    }

    /**
     * 在受控 worker 上按最新已登记事实恢复 learner 确认，不重复读取同步证据。
     */
    private CompletionStage<CompleteLearnerSyncResult> resumeLearner(ConfirmLearnerCommand command) {
        // 1. 转换操作身份并认领在途请求；重复立即返回，身份冲突拒绝。
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());
        BrokerId brokerId = new BrokerId(command.brokerId());
        MembershipEndpoint membershipEndpoint = new MembershipEndpoint(command.membershipEndpoint());

        LearnerConfirmationClaim claim = membershipOperationService.claimLearnerConfirmation(
                new LearnerConfirmationIdentity(operationId, brokerId, expectedEpoch, membershipEndpoint));
        switch (claim.status()) {
            case IN_PROGRESS:
                return CompletableFuture.completedStage(CompleteLearnerSyncResult.inProgress(operationId));
            case CONFLICT:
                return CompletableFuture.completedStage(CompleteLearnerSyncResult.inFlightConflict(operationId, expectedEpoch));
            case ACQUIRED:
                break;
        }

        LearnerConfirmationAttempt executionAttempt = claim.executionAttempt();
        CompletableFuture<CompleteLearnerSyncResult> completion = new CompletableFuture<>();
        CompletionStage<CompleteLearnerSyncResult> resultView = completion.minimalCompletionStage();

        // 2. 预留永久预算并绑定 generation；失效或拒绝时精确移除本轮记录。
        Executor execution = permitExecutor.reserveLearnerExecution(() -> {
            // 本轮执行资格已失效：释放恢复占用，并通知调用方不能继续。
            CompleteLearnerSyncResult result = CompleteLearnerSyncResult.notLeader(
                    operationId, expectedEpoch, "learner recovery role generation is no longer current");
            membershipOperationService.releaseLearnerConfirmation(executionAttempt);
            completion.complete(result);
        });
        if (execution == null) {
            membershipOperationService.releaseLearnerConfirmation(executionAttempt);
            return CompletableFuture.completedStage(CompleteLearnerSyncResult.unavailable(
                    operationId, expectedEpoch, "learner recovery outcome is unknown"));
        }

        // 3. 在 worker 重读已提交事实并校验身份；操作消失、拒绝或已成功时提前结束。
        return CompletableFuture.<LearnerConfirmationState>supplyAsync(() -> {
                    Optional<LeaderTerm> expectedTerm = brokerRaftAdapter.getLocalBroker().getLeaderTerm();
                    BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
                    LearnerSyncCompletionIntent intent = LearnerSyncCompletionIntent.builder()
                            .operationId(operationId)
                            .commandDigest(command.digest())
                            .expectedRevision(cluster.revision())
                            .brokerId(brokerId)
                            .changedAt(Instant.now()).build();
                    if (!cluster.exists(operationId)) {
                        return new LearnerConfirmationState.Finished(CompleteLearnerSyncResult.rejected(
                                operationId, cluster.epoch(), "learner recovery operation no longer exists"));
                    }
                    BrokerClusterDecision decision = cluster.confirmLearner(intent);
                    if (!decision.accepted()) {
                        return new LearnerConfirmationState.Finished(CompleteLearnerSyncResult.failure(operationId, cluster.epoch(), decision));
                    }
                    if (decision.operation().phase() == BrokerClusterOperationPhase.SUCCEEDED) {
                        return new LearnerConfirmationState.Finished(
                                BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncResultFrom(
                                        operationId, cluster.epoch(), BrokerClusterConsensusResult.duplicate(
                                                decision.operation(), decision.reason().value())));
                    }
                    return new LearnerConfirmationState.Prepared(cluster, expectedTerm, intent, decision.operation());
                }, execution)
                // 4. 准备后一次分流；只有待恢复操作进入提交链，不重新取证。
                .thenComposeAsync(state -> {
                    if (state instanceof LearnerConfirmationState.Finished(CompleteLearnerSyncResult result)) {
                        return CompletableFuture.completedStage(result);
                    }
                    LearnerConfirmationState.Prepared prepared = (LearnerConfirmationState.Prepared) state;
                    if (!matchesCurrentLeaderTerm(prepared.expectedTerm())) {
                        return CompletableFuture.completedStage(
                                BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncResultFrom(
                                        operationId, prepared.cluster().epoch(), BrokerClusterConsensusResult.notLeader(
                                                brokerRaftAdapter.getLeaderEndpoint(), "learner recovery Leader term changed")));
                    }
                    // 已提交的操作跳过登记，其他操作沿用原身份恢复 Begin。
                    CompletionStage<BrokerClusterConsensusResult> begin = prepared.operation().phase() == BrokerClusterOperationPhase.RAFT_COMMITTED
                            ? CompletableFuture.completedStage(BrokerClusterConsensusResult.applied(prepared.operation()))
                            : brokerRaftAdapter.beginOperation(prepared.operation());
                    return begin
                            // 5. 与新确认不同，恢复 Begin 返回 DUPLICATE 仍允许推进 PENDING。
                            .thenComposeAsync(registered -> {
                                if (!registered.hasPendingOperation()) {
                                    return CompletableFuture.completedStage(registered);
                                }
                                if (!matchesCurrentLeaderTerm(prepared.expectedTerm())) {
                                    return CompletableFuture.completedStage(BrokerClusterConsensusResult.notLeader(
                                            brokerRaftAdapter.getLeaderEndpoint(), "learner recovery commit Leader term changed"));
                                }
                                return brokerRaftAdapter.commitOperation(registered.operation());
                            }, execution)
                            // 6. 为已提交且尚未完成的操作补终态，提交前再次复验任期。
                            .thenComposeAsync(committed -> {
                                if (!committed.hasCommittedOperationToComplete()) {
                                    return CompletableFuture.completedStage(committed);
                                }
                                if (!matchesCurrentLeaderTerm(prepared.expectedTerm())) {
                                    return CompletableFuture.completedStage(BrokerClusterConsensusResult.notLeader(
                                            brokerRaftAdapter.getLeaderEndpoint(), "learner recovery completion Leader term changed"));
                                }
                                return brokerRaftAdapter.completeMembershipOperation(committed.operation());
                            }, execution)
                            // 7. 保留已关联操作的原始 epoch。
                            .thenApplyAsync(completed -> BrokerClusterAppTransformer.INSTANCE.completeLearnerSyncResultFrom(
                                    operationId, prepared.cluster().epoch(), completed), execution);
                }, execution)
                .handleAsync((result, failure) -> failure == null ? result : CompleteLearnerSyncResult.unavailable(
                        operationId, expectedEpoch, "learner recovery outcome is unknown"), execution)
                // 8. 经 worker 门禁发布；先精确移除，再完成执行者内部信号。
                .thenApplyAsync(result -> {
                    membershipOperationService.releaseLearnerConfirmation(executionAttempt);
                    completion.complete(result);
                    return result;
                }, execution)
                // 9. 汇合换代失效结果，返回只读视图以隔离调用方取消。
                .applyToEither(resultView, result -> result)
                .minimalCompletionStage();
    }

    /**
     * 提交成员 operation；应用服务拥有请求身份和本轮尝试，共识状态仍由已提交聚合拥有。
     */
    private CompletionStage<BrokerClusterConsensusResult> submitMembershipOperation(BrokerClusterOperation operation) {
        // 1. 领域服务判断请求冲突、在途共享和身份容量。
        MembershipOperationClaim claim = membershipOperationService.claimMembershipOperation(operation);
        if (claim.status() != Status.ACQUIRED) {
            return claim.resultView().thenApply(BrokerMembershipTransformer.INSTANCE::membershipConsensusResultFrom);
        }
        MembershipOperationAttempt newAttempt = claim.executionAttempt();
        // 2. 投递到绑定 generation 的 worker；业务执行及结果发布都使用同一阶段执行器。
        MembershipExecutionAdmissionResult admission = permitExecutor.submitApplicationStage(
                brokerRaftAdapter::getFollowingObservation,
                stages -> {
                    try {
                        processMembershipOperation(newAttempt, stages).whenCompleteAsync((result, failure) -> {
                            BrokerClusterConsensusResult outcome = failure == null ? result
                                    : BrokerClusterConsensusResult.unavailable(null, "membership outcome unknown");
                            // 领域服务先释放确定未提交的拒绝，再通知调用方，允许同步重试。
                            membershipOperationService.completeMembershipOperation(newAttempt,
                                    BrokerMembershipTransformer.INSTANCE.membershipOperationOutcomeFrom(outcome));
                        }, stages);
                    } catch (RuntimeException failure) {
                        membershipOperationService.completeMembershipOperation(newAttempt,
                                BrokerMembershipTransformer.INSTANCE.membershipOperationOutcomeFrom(
                                        BrokerClusterConsensusResult.unavailable(null, "membership outcome unknown")));
                    }
                }, () -> {
                    BrokerClusterConsensusResult invalidated = BrokerClusterConsensusResult.notLeader(
                            brokerRaftAdapter.getLeaderEndpoint(), "membership operation generation changed");
                    membershipOperationService.completeMembershipOperation(newAttempt,
                            BrokerMembershipTransformer.INSTANCE.membershipOperationOutcomeFrom(invalidated));
                });
        // 3. 投递未接纳时仅释放未保留的身份，不删除并发替换后的尝试。
        if (!admission.accepted()) {
            membershipOperationService.rejectMembershipAdmission(newAttempt,
                    BrokerMembershipTransformer.INSTANCE.membershipAdmissionOutcomeFrom(admission));
        }
        // 4. 领域共享视图映射为边界结果，取消派生 Future 不影响执行事实。
        return claim.resultView().thenApply(BrokerMembershipTransformer.INSTANCE::membershipConsensusResultFrom);
    }

    /**
     * 执行器回调的成员阶段：检查 Leader/幂等，登记 Begin，然后进入步骤推进。
     */
    private CompletionStage<BrokerClusterConsensusResult> processMembershipOperation(
            MembershipOperationAttempt attempt, Executor stages) {
        BrokerClusterOperation requested = attempt.operation();
        // 1. 固定本轮 Leader 任期，后续异步边界都复验该任期。
        Optional<LeaderTerm> term = brokerRaftAdapter.getLocalBroker().getLeaderTerm();
        if (!matchesCurrentLeaderTerm(term)) {
            return CompletableFuture.completedFuture(BrokerClusterConsensusResult.notLeader(
                    brokerRaftAdapter.getLeaderEndpoint(), "membership requires current Leader"));
        }
        // 2. 读取已提交 operation；重复请求按摘要和阶段恢复。
        BrokerCluster snapshot = brokerRaftAdapter.getClusterSnapshot();
        BrokerClusterOperation stored = snapshot.findOperation(requested.id()).orElse(null);
        if (stored != null) {
            attempt.retainIdentity();
            BrokerClusterDecision repeated = stored.checkRepeatedIntent(
                    BrokerClusterRaftTransformer.INSTANCE
                            .beginCommandFrom(requested).proposal());
            if (!repeated.accepted() || stored.phase() == BrokerClusterOperationPhase.SUCCEEDED) {
                return CompletableFuture.completedFuture(new BrokerClusterConsensusResult(
                        BrokerClusterConsensusResult.Status.valueOf(repeated.status().name()),
                        stored, null, repeated.reason().value()));
            }
        } else {
            BrokerClusterConsensusResult admission = checkFreshMembershipAdmission(snapshot, term);
            if (!admission.accepted()) {
                return CompletableFuture.completedFuture(admission);
            }
        }
        // 3. 新操作先登记 Begin，历史操作直接从已提交阶段继续。
        // 先封闭回收资格：即使 Begin 抛异常或返回未知，也可能已提交到 Raft。
        attempt.retainIdentity();
        CompletionStage<BrokerClusterConsensusResult> begin = stored == null
                ? brokerRaftAdapter.beginOperation(requested)
                : CompletableFuture.completedFuture(BrokerClusterConsensusResult.applied(stored));
        // 4. 按步骤执行 native membership、drain 和 Commit。
        return begin.thenComposeAsync(result -> advanceMembershipOperation(result, term, stages), stages);
    }

    /**
     * 按 operation 当前步骤推进成员变更：恢复当前 Permit，或创建 Permit 后执行下一步；
     * 所有异步阶段都在受控 worker 中复验任期和 revision。
     */
    private CompletionStage<BrokerClusterConsensusResult> advanceMembershipOperation(
            BrokerClusterConsensusResult previous, Optional<LeaderTerm> term, Executor stages) {
        // 1. Begin/恢复失败不再触碰 native membership。
        if (!previous.accepted()) {
            return CompletableFuture.completedFuture(previous);
        }
        if (!matchesCurrentLeaderTerm(term)) {
            return CompletableFuture.completedFuture(BrokerClusterConsensusResult.notLeader(
                    brokerRaftAdapter.getLeaderEndpoint(), "membership Leader term changed"));
        }
        // 2. 读取最新 committed operation，避免使用异步流程开始时的旧快照。
        BrokerCluster snapshot = brokerRaftAdapter.getClusterSnapshot();
        BrokerClusterOperation operation = snapshot.findOperation(previous.operation().id()).orElseThrow();
        if (operation.phase() == BrokerClusterOperationPhase.RAFT_COMMITTED) {
            return completeMembershipOperation(BrokerClusterConsensusResult.applied(operation));
        }
        if (operation.phase() != BrokerClusterOperationPhase.PENDING) {
            return CompletableFuture.completedFuture(BrokerClusterConsensusResult.applied(operation));
        }
        // 3. 没有待执行步骤时，只有全部步骤排空后才能 Commit。
        BrokerMembershipStep step = operation.currentMembershipStep();
        if (step == null) {
            if (!operation.membershipReadyToCommit()) {
                return CompletableFuture
                        .completedFuture(BrokerClusterConsensusResult.rejected("membership steps are not drained"));
            }
            return brokerRaftAdapter.commitOperation(operation, snapshot.revision())
                    .thenComposeAsync(result -> {
                        if (!matchesCurrentLeaderTerm(term)) {
                            return CompletableFuture.completedFuture(BrokerClusterConsensusResult.notLeader(
                                    brokerRaftAdapter.getLeaderEndpoint(),
                                    "membership completion Leader term changed"));
                        }
                        return completeMembershipOperation(result);
                    }, stages);
        }
        // 4. 已登记但未排空的 Permit 只能恢复，不能重新获得 native 能力。
        BrokerMembershipPermit existing = step.currentPermit();
        CompletionStage<BrokerClusterConsensusResult> execution;
        if (existing != null && !existing.drained()) {
            execution = executeMembershipStep(existing);
        } else {
            // 5. 已排空但未成功的步骤由下一轮恢复创建同目标的新 Permit。
            String permitId = GeneratorUtils.nextUUID();
            BrokerId localId = brokerRaftAdapter.getLocalBroker().getId();
            permitExecutor.reserve(operation.id(), step.stepId(), permitId, step.target());
            RegisterMembershipPermitCommand command = RegisterMembershipPermitCommand.builder()
                    .operationId(operation.id())
                    .stepId(step.stepId())
                    .permitId(permitId)
                    .executorBrokerId(localId)
                    .executorIncarnation(permitExecutor.incarnation())
                    .expectedRevision(snapshot.revision())
                    .build();
            execution = brokerRaftAdapter.registerMembershipPermit(command)
                    .thenComposeAsync(registered -> {
                        if (!registered.accepted()) {
                            return CompletableFuture.completedFuture(registered);
                        }
                        if (!matchesCurrentLeaderTerm(term)) {
                            return CompletableFuture.completedFuture(BrokerClusterConsensusResult.notLeader(
                                    brokerRaftAdapter.getLeaderEndpoint(), "membership registration Leader term changed"));
                        }
                        BrokerClusterOperation committed = brokerRaftAdapter.findOperation(operation.id()).orElseThrow();
                        BrokerMembershipStep current = committed.currentMembershipStep();
                        if (current == null || current.currentPermit() == null
                                || !current.currentPermit().permitId().equals(permitId)) {
                            return CompletableFuture.completedFuture(
                                    BrokerClusterConsensusResult.rejected("registered permit is no longer current"));
                        }
                        return executeMembershipStep(current.currentPermit());
                    }, stages);
        }
        return execution.thenComposeAsync(result -> {
            if (!result.accepted()) {
                return CompletableFuture.completedFuture(result);
            }
            BrokerClusterOperation current = brokerRaftAdapter.findOperation(operation.id()).orElseThrow();
            BrokerMembershipStep completed = current.findMembershipStep(step.stepId()).orElseThrow();
            if (!completed.successful()) {
                return CompletableFuture.completedFuture(BrokerClusterConsensusResult.unavailable(null,
                        "membership target was not applied; explicit recovery required"));
            }
            return advanceMembershipOperation(result, term, stages);
        }, stages);
    }

    /**
     * 消费已登记 Permit，并把 native 结果转换为排空确认流程。
     */
    private CompletionStage<BrokerClusterConsensusResult> executeMembershipStep(
            BrokerMembershipPermit permit) {
        return permitExecutor.execute(permit, () -> {
                    if (!brokerRaftAdapter.getLocalBroker().isLeader()) {
                        // 未调用原生入口；此异常不充当可移交许可的排空证明。
                        throw new IllegalStateException("membership executor is no longer Leader");
                    }
                    BrokerClusterOperation current = brokerRaftAdapter.findOperation(permit.operationId()).orElseThrow();
                    BrokerMembershipStep currentStep = current.currentMembershipStep();
                    if (currentStep == null || !permit.equals(currentStep.currentPermit())) {
                        throw new SecurityException("membership execution permit is no longer current");
                    }
                    return brokerRaftAdapter.changeMembershipConfiguration(permit.target());
                })
                .thenComposeAsync(result -> {
                            if (!permitExecutor.isCurrentGeneration(permit)) {
                                return CompletableFuture.completedFuture(new MembershipConfirmationResult(
                                        MembershipConfirmationResult.Status.UNKNOWN, null,
                                        "membership permit generation is no longer current"));
                            }
                            return sendExecutionResultToLeader(permit);
                        },
                        permitExecutor.nativeConfirmationExecutor(permit))
                .thenApply(response -> {
                    if (!permitExecutor.isCurrentGeneration(permit)) {
                        return BrokerClusterConsensusResult.notLeader(
                                brokerRaftAdapter.getLeaderEndpoint(), "membership permit generation changed");
                    }
                    return switch (response.status()) {
                        case COMMITTED, DUPLICATE -> BrokerClusterConsensusResult.applied(
                                brokerRaftAdapter.findOperation(permit.operationId()).orElseThrow());
                        case NOT_LEADER ->
                                BrokerClusterConsensusResult.notLeader(response.leaderEndpoint(), response.message());
                        case REJECTED -> BrokerClusterConsensusResult.rejected(response.message());
                        case UNKNOWN ->
                                BrokerClusterConsensusResult.unavailable(response.leaderEndpoint(), response.message());
                    };
                });
    }

    /**
     * 根据当前 Leader 路由已排空事实；路由不可用时保留未知结果。
     */
    private CompletionStage<MembershipConfirmationResult> sendExecutionResultToLeader(
            BrokerMembershipPermit permit) {
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        boolean localLeader = brokerRaftAdapter.getLocalBroker().isLeader();
        String leaderEndpoint = brokerRaftAdapter.getLeaderEndpoint();
        if (localLeader) {
            return sendExecutionResult(
                    permit.operationId(), brokerRaftAdapter.getLocalBroker().getId());
        }
        if (leaderEndpoint == null || leaderEndpoint.isBlank()) {
            return CompletableFuture.completedFuture(new MembershipConfirmationResult(
                    MembershipConfirmationResult.Status.UNKNOWN,
                    leaderEndpoint, "current Leader confirmation route is unavailable"));
        }
        MembershipEndpoint leaderMembershipEndpoint = new MembershipEndpoint(leaderEndpoint);
        BrokerNode receiver = cluster.findNodeByMembershipEndpoint(leaderMembershipEndpoint).orElse(null);
        if (receiver == null) {
            return CompletableFuture.completedFuture(new MembershipConfirmationResult(
                    MembershipConfirmationResult.Status.UNKNOWN,
                    leaderEndpoint, "current Leader confirmation route is unavailable"));
        }
        return sendExecutionResult(permit.operationId(), receiver.id());
    }

    /**
     * 执行器回调的 drain 重传阶段：只发送已证明排空的事实。
     */
    private CompletionStage<MembershipConfirmationResult> sendRecordedExecutionResult(
            ClusterOperationId operationId, BrokerId receiverBrokerId) {
        BrokerCluster snapshot = brokerRaftAdapter.getClusterSnapshot();
        BrokerClusterOperation operation = snapshot.findOperation(operationId).orElseThrow();
        // 先拒绝无效结构，再采集本机事实；采样不代替发送前的生命周期复验。
        operation.findPendingMembershipConfirmation();
        Map<BrokerMembershipPermit, BrokerMembershipPermit.Result> localResults = new HashMap<>();
        for (BrokerMembershipStep step : operation.membershipSteps()) {
            BrokerMembershipPermit permit = step.currentPermit();
            if (permit != null) {
                localResults.put(permit, permitExecutor.localDrainedResult(permit));
            }
        }
        BrokerMembershipStep selected = membershipOperationService.selectDrainedMembershipStep(operation, localResults);
        BrokerMembershipPermit permit = selected.currentPermit();
        BrokerMembershipPermit.Result result = permitExecutor.localDrainedResult(permit);
        if (result == BrokerMembershipPermit.Result.UNKNOWN) {
            throw new SecurityException("native execution is not proven drained");
        }
        BrokerNode receiver = snapshot.findNode(receiverBrokerId).orElseThrow();
        if (!snapshot.isMembershipTargetVoter(receiverBrokerId, selected)) {
            throw new SecurityException("receiver is not a trusted target voter");
        }
        ConfirmMembershipPermitDrainedParams request = ConfirmMembershipPermitDrainedParams.builder()
                .clusterId(brokerRaftAdapter.getClusterId().value())
                .callerBrokerId(permit.executorBrokerId().value())
                .receiverBrokerId(receiverBrokerId.value())
                .operationId(operationId.value())
                .stepId(selected.stepId())
                .permitId(permit.permitId())
                .executorBrokerId(permit.executorBrokerId().value())
                .executorIncarnation(permit.executorIncarnation())
                .targetDigest(selected.targetDigest())
                .result(BrokerMembershipTransformer.INSTANCE.drainedResultFrom(result))
                .build();
        // 在最后一个外部入口前复验关闭／故障；只发送已选定的同一事实。
        if (permitExecutor.localDrainedResult(permit) != result) {
            throw new SecurityException("drain lifecycle is closed");
        }
        BrokerId localBrokerId = brokerRaftAdapter.getLocalBroker().getId();
        if (!receiverBrokerId.equals(localBrokerId)) {
            return brokerPeerAdapter.confirmMembershipPermitDrained(receiver, request)
                    .thenApply(BrokerMembershipTransformer.INSTANCE::membershipConfirmationResultFrom);
        }
        // 本机调用的可信执行者来自 Permit，不使用请求自报身份替代认证身份。
        AcceptMembershipPermitDrainedCommand command = BrokerMembershipTransformer.INSTANCE
                .acceptMembershipPermitDrainedCommandFrom(request, permit.executorBrokerId());
        return confirmExecution(command);
    }

    private BrokerMembershipExecutor.ConfirmationRound prepareExecutionConfirmation(
            AcceptMembershipPermitDrainedCommand request) {
        // 1. 校验上报者；失败时不读取本机信息。
        BrokerId callerId = new BrokerId(request.callerBrokerId());
        BrokerId executorId = new BrokerId(request.executorBrokerId());
        BrokerId authenticatedExecutorId = new BrokerId(request.authenticatedExecutorBrokerId());
        if (!membershipOperationService.matchesExecutionReporter(callerId, executorId, authenticatedExecutorId)) {
            return BrokerMembershipExecutor.ConfirmationRound.completed(MembershipConfirmationResult.identityMismatch());
        }
        // 2. 校验接收目标；集群不匹配时不读取本机 Broker。
        BrokerClusterId requestedCluster = new BrokerClusterId(request.clusterId());
        BrokerClusterId localCluster = brokerRaftAdapter.getClusterId();
        if (!requestedCluster.equals(localCluster)) {
            return BrokerMembershipExecutor.ConfirmationRound.completed(MembershipConfirmationResult.identityMismatch());
        }
        BrokerId receiverId = new BrokerId(request.receiverBrokerId());
        BrokerId localBrokerId = brokerRaftAdapter.getLocalBroker().getId();
        if (!receiverId.equals(localBrokerId)) {
            return BrokerMembershipExecutor.ConfirmationRound.completed(MembershipConfirmationResult.identityMismatch());
        }
        // 3. 定位执行记录，准入通过后才提交给执行器。
        MembershipExecutionAdmission admission = membershipOperationService.admitExecutionConfirmation(
                brokerRaftAdapter.getClusterSnapshot(), new ClusterOperationId(request.operationId()), request.stepId());
        if (admission.status() == MembershipExecutionAdmission.Status.REJECTED) {
            return BrokerMembershipExecutor.ConfirmationRound.completed(
                    MembershipConfirmationResult.rejected(admission.reason()));
        }
        return submitExecutionConfirmation(admission.permit(), request);
    }

    /**
     * 将已定位的执行记录交给执行器；只将此处的准入异常转换为未知结果。
     */
    private BrokerMembershipExecutor.ConfirmationRound submitExecutionConfirmation(
            BrokerMembershipPermit permit, AcceptMembershipPermitDrainedCommand request) {
        try {
            return permitExecutor.confirm(permit, BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(request),
                    stages -> confirmExecution(request, stages));
        } catch (RuntimeException failure) {
            return BrokerMembershipExecutor.ConfirmationRound.completed(new MembershipConfirmationResult(
                    MembershipConfirmationResult.Status.UNKNOWN, null, "confirmation admission unavailable"));
        }
    }

    /**
     * 确认一次成员变更执行已经结束，并记录其结果。
     * 先取得有效的集群读取证据，再校验执行记录；无法确认时不允许据此推进后续执行。
     */
    private CompletionStage<MembershipConfirmationResult> confirmExecution(
            AcceptMembershipPermitDrainedCommand request, Executor stages) {
        // 1. worker 消费前确认本机仍是 Leader；否则不发起 quorum 读取。
        if (!brokerRaftAdapter.getLocalBroker().isLeader()) {
            return CompletableFuture.completedFuture(new MembershipConfirmationResult(
                    MembershipConfirmationResult.Status.NOT_LEADER,
                    brokerRaftAdapter.getLeaderEndpoint(), "confirmation requires current Leader"));
        }
        // 2. 获取同任期的 quorum 读取，并确认读取已在本机应用。
        return brokerRaftAdapter.confirmMembershipPermitDrain(new QuorumReadNonce(GeneratorUtils.nextUUID()))
                .thenComposeAsync(read -> {
                    if (!brokerRaftAdapter.isMembershipPermitDrainCurrent(read)) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("drain proof role changed before worker consumption"));
                    }
                    return brokerRaftAdapter.fetchQuorumReadIndex().thenApply(index -> {
                        if (index < read.readIndex().value()) {
                            throw new IllegalStateException("quorum read is not applied locally");
                        }
                        return read;
                    });
                }, stages)
                // 3. 等待后再次确认领导权和读取证据有效，并核对已登记执行的身份与结果。
                .thenComposeAsync(read -> {
                    if (!brokerRaftAdapter.isMembershipPermitDrainCurrent(read)
                            || !brokerRaftAdapter.getLocalBroker().getLeaderTerm().filter(read.leaderTerm()::equals).isPresent()) {
                        return CompletableFuture.completedFuture(BrokerClusterConsensusResult
                                .notLeader(brokerRaftAdapter.getLeaderEndpoint(), "Leader term changed"));
                    }
                    ConfirmMembershipPermitDrainedCommand command = BrokerMembershipTransformer.INSTANCE
                            .confirmMembershipPermitDrainedCommandFrom(request);
                    BrokerCluster snapshot = brokerRaftAdapter.getClusterSnapshot();
                    BrokerClusterDecision decision = snapshot.checkMembershipExecutionConfirmation(
                            BrokerMembershipTransformer.INSTANCE.membershipExecutionConfirmationFrom(command));
                    if (!decision.accepted()) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterConsensusResult.rejected("stored permit identity or result mismatch"));
                    }
                    if (!brokerRaftAdapter.isMembershipPermitDrainCurrent(read)) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterConsensusResult.notLeader(null, "drain proof role changed before submit"));
                    }
                    // 4. 提交前最后复验证据，将本次执行结束及结果写入集群记录。
                    return brokerRaftAdapter.confirmMembershipPermitDrained(command);
                }, stages)
                // 5. 将共识状态映射为对外排空响应；异常保持 UNKNOWN。
                .thenApplyAsync(result -> new MembershipConfirmationResult(
                        switch (result.status()) {
                            case APPLIED -> MembershipConfirmationResult.Status.COMMITTED;
                            case DUPLICATE -> MembershipConfirmationResult.Status.DUPLICATE;
                            case NOT_LEADER -> MembershipConfirmationResult.Status.NOT_LEADER;
                            case REJECTED, CONFLICT -> MembershipConfirmationResult.Status.REJECTED;
                            case UNAVAILABLE, IN_PROGRESS -> MembershipConfirmationResult.Status.UNKNOWN;
                        }, result.leaderEndpoint(), result.message()), stages)
                .exceptionallyAsync(failure -> new MembershipConfirmationResult(
                        MembershipConfirmationResult.Status.UNKNOWN,
                        brokerRaftAdapter.getLeaderEndpoint(), "confirmation outcome is unknown"), stages);
    }

    /**
     * 获取 learner 的共识成员身份及日志进度证据。
     *
     * <p>
     * 只观察 Leader 成员列表并读取 quorum ReadIndex 和目标节点已应用位置，不登记
     * operation，也不修改集群拓扑。
     * </p>
     *
     * @param learner            目标 learner 的领域节点
     * @param membershipEndpoint 目标节点在 Raft 成员配置中的端点
     * @return 证据检查结果；APPLIED 表示可以继续登记同步确认操作
     */
    private CompletionStage<BrokerClusterConsensusResult> verifyLearnerCaughtUp(
            BrokerNode learner, MembershipEndpoint membershipEndpoint,
            Optional<LeaderTerm> expectedTerm, Executor stages) {
        // 1. 本机 Raft 节点不是集群 Leader，返回检查失败及已知 Leader 地址。
        if (!brokerRaftAdapter.getLocalBroker().isLeader()) {
            return CompletableFuture.completedFuture(
                    BrokerClusterConsensusResult.notLeader(brokerRaftAdapter.getLeaderEndpoint(),
                            "learner progress check requires the cluster leader"));
        }
        // 2. 检查 Leader 当前 learners 观察中包含目标（不是已应用配置证明），再比较 quorum ReadIndex 与节点应用位置。
        if (!brokerRaftAdapter.readLeaderMembershipConfiguration().learners().contains(membershipEndpoint)) {
            return CompletableFuture.completedFuture(
                    BrokerClusterConsensusResult
                            .rejected("learner is absent from the Leader configuration observation"));
        }
        return brokerRaftAdapter.confirmLeadership(new QuorumReadNonce(GeneratorUtils.nextUUID()))
                .thenComposeAsync(read -> brokerPeerAdapter.fetchQuorumReadIndex(learner)
                        .thenApplyAsync(index -> {
                            if (expectedTerm.isEmpty()
                                    || !expectedTerm.get().equals(read.leaderTerm())
                                    || !brokerRaftAdapter.getLocalBroker().isLeader()
                                    || !brokerRaftAdapter.getLocalBroker().getLeaderTerm().equals(expectedTerm)) {
                                return BrokerClusterConsensusResult.notLeader(brokerRaftAdapter.getLeaderEndpoint(),
                                        "learner progress evidence Leader term changed");
                            }
                            if (index >= read.readIndex().value()) {
                                return BrokerClusterConsensusResult.applied();
                            } else {
                                return BrokerClusterConsensusResult.rejected("learner has not applied the committed Raft read index");
                            }
                        }, stages)
                        // 3. 证据获取失败不登记操作；区分失去领导权与当前 Leader 暂不可用。
                        .exceptionallyAsync(failure -> {
                            if (failure instanceof CompletionException && failure.getCause() != null) {
                                failure = failure.getCause();
                            }
                            if (brokerRaftAdapter.getLocalBroker().isLeader()) {
                                return BrokerClusterConsensusResult.unavailable(brokerRaftAdapter.getLeaderEndpoint(), failure.getMessage());
                            } else {
                                return BrokerClusterConsensusResult.notLeader(brokerRaftAdapter.getLeaderEndpoint(), failure.getMessage());
                            }
                        }, stages), stages);
    }

    /**
     * 判断本机仍是 Leader，且当前任期匹配预期任期。
     */
    private boolean matchesCurrentLeaderTerm(Optional<LeaderTerm> expectedTerm) {
        return expectedTerm.isPresent()
                && brokerRaftAdapter.getLocalBroker().isLeader()
                && brokerRaftAdapter.getLocalBroker().getLeaderTerm().equals(expectedTerm);
    }

    /**
     * 将已提交成员拓扑的 operation 推进到 SUCCEEDED。
     *
     * <p>
     * 只有操作处于已提交、尚未完成的阶段时才写入终态；失败、未提交或已经进入终态的
     * 结果直接返回，避免重复写入完成命令。
     * </p>
     *
     * @param result 成员变更的共识处理结果
     * @return 终态提交结果或原结果
     */
    private CompletionStage<BrokerClusterConsensusResult> completeMembershipOperation(
            BrokerClusterConsensusResult result) {
        // 1. 失败、尚未提交或已进入终态的操作直接返回，不重复推进阶段。
        if (!result.hasCommittedOperationToComplete()) {
            return CompletableFuture.completedFuture(result);
        }
        // 2. 持久化成功终态，创建与完成时间统一为毫秒精度，允许同毫秒完成。
        return brokerRaftAdapter.completeMembershipOperation(result.operation());
    }

    /**
     * 执行器回调的诊断阶段：只读检查，不创建 operation 或 Permit。
     */
    private void inspectMembershipConfiguration() {
        BrokerCluster snapshot = brokerRaftAdapter.getClusterSnapshot();
        if (snapshot.hasPendingMembershipOperation()) {
            return;
        }
        checkFreshMembershipAdmission(snapshot, brokerRaftAdapter.getLocalBroker().getLeaderTerm());
    }

    /**
     * 校验 committed 拓扑、Leader 原生配置及读取期间的任期/revision。
     */
    private BrokerClusterConsensusResult checkFreshMembershipAdmission(BrokerCluster snapshot, Optional<LeaderTerm> term) {
        try {
            // 1. 从 committed BrokerCluster 构造业务侧期望配置。
            ConsensusMembership expected = snapshot.expectedMembership();
            // 2. 读取 Leader 原生配置，仅用于准入诊断，不执行修复。
            ConsensusMembership actual = brokerRaftAdapter.readLeaderMembershipConfiguration();
            // 3. 先复验 Leader 任期和 committed revision，避免用失效快照发布漂移。
            if (!matchesCurrentLeaderTerm(term)
                    || !brokerRaftAdapter.getClusterSnapshot().revision().equals(snapshot.revision())) {
                diagnosticMetrics.recordUnavailable();
                return BrokerClusterConsensusResult.rejected("membership admission term or revision changed");
            }
            diagnosticMetrics.recordComparison(expected.equals(actual));
            if (!expected.equals(actual)) {
                log.warn("Broker membership drift blocks maintenance: expected={}, actual={}, revision={}, leaderTerm={}", expected, actual, snapshot.revision(), term);
                return BrokerClusterConsensusResult.rejected("membership configuration drift blocks maintenance");
            }
            return BrokerClusterConsensusResult.applied();
        } catch (RuntimeException failure) {
            diagnosticMetrics.recordUnavailable();
            log.warn("Broker membership configuration diagnostic unavailable: revision={}, leaderTerm={}", snapshot.revision(), term, failure);
            return BrokerClusterConsensusResult.unavailable(null, "membership configuration diagnostic unavailable");
        }
    }

    /**
     * learner 确认与恢复链的私有不可变阶段状态，不属于 CQRS 或持久化模型。
     */
    private sealed interface LearnerConfirmationState {
        record Prepared(
                /* 本轮资格检查使用的独立聚合快照。 */
                BrokerCluster cluster,
                /* 首次读取的本机 Leader 任期。 */
                Optional<LeaderTerm> expectedTerm,
                /* 冻结的确认意图及 expectedRevision。 */
                LearnerSyncCompletionIntent intent,
                /* 领域接受的新操作。 */
                BrokerClusterOperation operation) implements LearnerConfirmationState {
        }

        record Finished(
                /* 准备后直接返回的业务响应，不进入提交链。 */
                CompleteLearnerSyncResult result) implements LearnerConfirmationState {
        }
    }
}
