package com.co.kc.imchat.broker.adapter.cluster;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitRoutingEpochFenceCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.support.raft.codec.BrokerClusterCommandCodec;
import com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.plugin.raft.core.RaftRuntime;
import com.co.kc.imchat.plugin.raft.model.RaftMembership;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.plugin.raft.model.RaftNodeSettings;
import com.co.kc.imchat.plugin.raft.model.RaftResult;
import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 本机 Raft 运行实例和 Broker 已提交状态的唯一技术所有者。
 *
 * <p>通用 RaftRuntime 负责共识与资源生命周期；Broker 状态机负责命令分派、
 * 快照与领域投影，BrokerCluster 在日志应用时重验业务规则。具名提交方法只构造命令，
 * 不执行 Broker 对端 RPC，也不协调 Gateway 投递。</p>
 * <p>本机读取可能落后，不自动提供线性一致保证；Lease 签发复用状态机同步边界。
 * 成员配置变更与业务拓扑日志是两类提交，不能将前者成功等同于后者已完成。</p>
 */
public final class BrokerRaftAdapter implements AutoCloseable {
    private static final String SNAPSHOT_FILE = "broker-cluster-state.json";

    @Getter
    private final BrokerClusterId clusterId;
    private final RaftBrokerClusterStateMachine clusterStateMachine;
    private final RaftRuntime<BrokerClusterConsensusCommand, BrokerClusterConsensusResult> raftRuntime;
    private final RoutingLeaseGrantService routingLeaseGrantService;
    /**
     * -- GETTER --
     * 返回本机 Broker 查询视图；视图复用唯一 Raft Runtime，不缓存动态角色。
     */
    @Getter
    private final LocalBroker localBroker;

    /**
     * 校验配置，创建唯一状态机和 Raft 实例；恢复失败向启动调用方传播。
     */
    public BrokerRaftAdapter(
            BrokerRaftProperties properties,
            BrokerClusterState clusterState,
            RoutingLeaseGrantService routingLeaseGrantService) throws IOException {
        properties.validate();
        this.clusterId = new BrokerClusterId(properties.getClusterId());
        this.routingLeaseGrantService = routingLeaseGrantService;
        this.clusterStateMachine = new RaftBrokerClusterStateMachine(
                properties.getClusterId(), clusterState, properties.getOperationHistoryCapacity(), properties.bootstrapMembershipEndpoints());

        RaftNodeSettings settings = RaftNodeSettings.builder()
                .clusterId(properties.getClusterId())
                .groupId(properties.getGroupId())
                .nodeId(properties.getNodeId())
                .endpoint(properties.getEndpoint())
                .dataDirectory(properties.dataPath())
                .voters(properties.getInitialVoters())
                .learners(properties.getInitialLearners())
                .electionTimeoutMillis(properties.getElectionTimeoutMs())
                .snapshotIntervalSeconds(Math.toIntExact(properties.getSnapshotInterval().toSeconds()))
                .commandTimeoutMillis(properties.getCommandTimeoutMs())
                .snapshotFileName(SNAPSHOT_FILE)
                .build();
        this.raftRuntime = new RaftRuntime<>(settings, BrokerClusterCommandCodec.INSTANCE, clusterStateMachine);
        this.localBroker = new LocalBroker(raftRuntime);
    }

    /**
     * 查询本机当前已知的集群 Leader 成员通信地址；未知时为空。
     */
    public String getLeaderEndpoint() {
        return raftRuntime.leaderEndpoint();
    }

    /**
     * 获取 quorum ReadIndex 并等待本机状态机应用到该位置，失败按原异步契约传播。
     */
    public CompletionStage<Long> fetchQuorumReadIndex() {
        return raftRuntime.fetchQuorumReadIndex(GeneratorUtils.nextUUID().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 取得同一 Leader 任期内的多数派读取证据；失败通过异步阶段传播。
     */
    public CompletionStage<QuorumRead> confirmLeadership(QuorumReadNonce nonce) {
        return raftRuntime.confirmLeadership(nonce.value().getBytes(StandardCharsets.UTF_8))
                .thenApply(read -> new QuorumRead(new LeaderTerm(read.term()), new QuorumReadIndex(read.index())));
    }

    /**
     * 返回分离的领域快照，调用方修改不会提交到权威状态。
     */
    public BrokerCluster getClusterSnapshot() {
        return clusterStateMachine.getCluster();
    }

    /**
     * 读取本机已应用状态的 SDK 投影；不隐式执行 quorum ReadIndex。
     */
    public BrokerClusterState getCommittedState() {
        return clusterStateMachine.committedState();
    }

    /**
     * 读取本机已应用的 operation，供幂等响应与恢复流程使用。
     */
    public Optional<BrokerClusterOperation> findOperation(ClusterOperationId id) {
        return clusterStateMachine.findOperation(id);
    }

    /**
     * 查询目标 epoch 的唯一未完成 drain；重复活跃操作由状态机拒绝。
     */
    public Optional<BrokerClusterOperation> findActiveDrainOperation(RoutingEpoch epoch) {
        return clusterStateMachine.findActiveDrainOperation(epoch);
    }

    /**
     * 查询目标 epoch 的唯一未完成 drain 或故障路由恢复操作。
     */
    public Optional<BrokerClusterOperation> findActiveRouteRecoveryOperation(RoutingEpoch epoch) {
        return clusterStateMachine.findActiveRouteRecoveryOperation(epoch);
    }

    /**
     * 读取本机日志已应用的 fence 事实，不代表所有目标已确认隔离。
     */
    public Optional<RoutingEpochFence> findRoutingFence() {
        return clusterStateMachine.findRoutingFence();
    }

    /**
     * 编码并提交命令；技术提交成功仍须检查状态机的业务结果，超时可能已提交。
     */
    private CompletionStage<BrokerClusterConsensusResult> submitCommand(
            BrokerClusterConsensusCommand command) {
        return raftRuntime.submit(command).thenApply(result -> result.applied()
                ? result.value() : BrokerClusterRaftTransformer.INSTANCE.consensusFailureResultFrom(result));
    }

    /**
     * 提交操作登记意图；revision、幂等与资格仍由日志应用时的聚合判断。
     */
    public CompletionStage<BrokerClusterConsensusResult> beginOperation(BrokerClusterOperation operation) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.beginCommandFrom(operation));
    }

    /**
     * 提交已登记操作的拓扑变更，沿用 operation 冻结的预期 revision。
     */
    public CompletionStage<BrokerClusterConsensusResult> commitOperation(BrokerClusterOperation operation) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.commitCommandFrom(operation, Instant.now()));
    }

    /**
     * 记录单步成员配置的明确拒绝，阶段资格仍由聚合核对。
     */
    public CompletionStage<BrokerClusterConsensusResult> failMembershipOperation(
            BrokerClusterOperation operation, BrokerConsensusMembershipResult membership) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.membershipFailureCommandFrom(operation, membership, Instant.now()));
    }

    /**
     * 成员拓扑已提交后登记成功终态；不执行成员配置变更。
     */
    public CompletionStage<BrokerClusterConsensusResult> completeMembershipOperation(
            BrokerClusterOperation operation) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.membershipCompletionCommandFrom(operation, Instant.now()));
    }

    public CompletionStage<BrokerClusterConsensusResult> failDrain(
            BrokerClusterOperation operation, OperationFailureReason reason, Instant changedAt) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.drainFailureCommandFrom(operation, reason, changedAt));
    }

    /**
     * 提交 Gateway 快照确认事实，由聚合核对冻结水位和操作身份。
     */
    public CompletionStage<BrokerClusterConsensusResult> confirmGatewayRoutes(
            BrokerClusterOperation operation, DrainProgressUpdate confirmation) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.gatewayConfirmationCommandFrom(operation, confirmation, Instant.now()));
    }

    public CompletionStage<BrokerClusterConsensusResult> saveRecoveryEvidence(
            BrokerClusterOperation operation, RouteRecoveryEvidence evidence, Instant changedAt) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.recoveryEvidenceCommandFrom(operation, evidence, changedAt));
    }

    /**
     * 携带应用层明确提供的预期 revision 请求完成排空，提交时重验全部证据。
     */
    public CompletionStage<BrokerClusterConsensusResult> completeDrain(
            BrokerClusterOperation operation, ClusterRevision expectedRevision) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.drainCompletionCommandFrom(operation, expectedRevision, Instant.now()));
    }

    /**
     * 携带预期 revision 和已确认风险原因请求强制结束，不绕过领域校验。
     */
    public CompletionStage<BrokerClusterConsensusResult> forceDrain(
            BrokerClusterOperation operation, ClusterRevision expectedRevision,
            OperationFailureReason reason) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.forcedDrainCommandFrom(operation, expectedRevision, reason, Instant.now()));
    }

    /**
     * 提交开始新 epoch 授权的阶段转换；不在这里安装远端 Lease。
     */
    public CompletionStage<BrokerClusterConsensusResult> beginEpochAuthorization(
            BrokerClusterOperation operation) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.operationPhaseCommandFrom(operation, BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, Instant.now()));
    }

    /**
     * 提交授权完成的阶段转换，后续路由重建仍由应用层推进。
     */
    public CompletionStage<BrokerClusterConsensusResult> completeEpochAuthorization(
            BrokerClusterOperation operation) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.operationPhaseCommandFrom(operation, BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                BrokerClusterOperationPhase.REBUILDING_ROUTES, null, Instant.now()));
    }

    /**
     * 提交确定的验证时间与超时策略，由聚合计算恢复结果及降级原因。
     */
    public CompletionStage<BrokerClusterConsensusResult> verifyRouteRecovery(
            BrokerClusterOperation operation, Duration recoveryTimeout, Instant changedAt) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.recoveryVerificationCommandFrom(operation, recoveryTimeout, changedAt));
    }

    /**
     * 请求结束已验证恢复，保持聚合已有的正常或降级结论。
     */
    public CompletionStage<BrokerClusterConsensusResult> completeRouteRecovery(
            BrokerClusterOperation operation) {
        return submitCommand(BrokerClusterRaftTransformer.INSTANCE.recoveryCompletionCommandFrom(operation, Instant.now()));
    }

    /**
     * 将 fence 写入共识日志；未被接受时返回失败阶段，不能伪造持久化成功。
     */
    public CompletionStage<RoutingEpochFence> commitRoutingFence(RoutingEpochFence operation) {
        return submitCommand(new CommitRoutingEpochFenceCommand(operation)).thenCompose(result -> result.accepted()
                ? CompletableFuture.completedFuture(operation)
                : CompletableFuture.failedFuture(new IllegalStateException("cannot persist fencing operation: " + result.message())));
    }

    /**
     * 读取 Raft 当前成员配置，不能替代 Broker 路由拓扑的 committedState。
     */
    public ConsensusMembership getConsensusMembership() {
        return BrokerClusterStateTransformer.INSTANCE.consensusMembershipFrom(raftRuntime.membership());
    }

    /**
     * 幂等加入 learner；已有 voter 明确拒绝，已有 learner 返回重复成功。
     */
    public CompletionStage<BrokerConsensusMembershipResult> addLearner(MembershipEndpoint endpoint) {
        RaftMembership membership = raftRuntime.membership();
        if (membership.voters().contains(endpoint.value())) {
            return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.rejected(
                    getLeaderEndpoint(), "endpoint is already a voter"));
        }
        if (membership.learners().contains(endpoint.value())) {
            return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.duplicate(getLeaderEndpoint(), "endpoint is already a learner"));
        }
        return raftRuntime.addLearner(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
    }

    /**
     * 先移除 learner 再加入 voter；只完成第一步时重试从当前配置继续。
     */
    public CompletionStage<BrokerConsensusMembershipResult> promoteLearner(MembershipEndpoint endpoint) {
        RaftMembership membership = raftRuntime.membership();
        if (membership.voters().contains(endpoint.value())) {
            return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.duplicate(
                    getLeaderEndpoint(), "endpoint is already a voter"));
        }
        if (!membership.learners().contains(endpoint.value())) {
            return raftRuntime.addVoter(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
        }
        // 首步成功、次步失败时不回滚已提交配置；重试走上面的缺失 learner 分支。
        return raftRuntime.removeLearner(endpoint.value())
                .thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom)
                .thenCompose(result -> {
                    if (!result.accepted()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    return raftRuntime.addVoter(endpoint.value())
                            .thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
                });
    }

    /**
     * 先移除 voter 再加入 learner；只完成第一步时重试补齐第二步。
     */
    public CompletionStage<BrokerConsensusMembershipResult> demoteVoter(MembershipEndpoint endpoint) {
        RaftMembership membership = raftRuntime.membership();
        if (membership.learners().contains(endpoint.value())) {
            return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.duplicate(getLeaderEndpoint(), "endpoint is already a learner"));
        }
        if (!membership.voters().contains(endpoint.value())) {
            return raftRuntime.addLearner(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
        }
        // 首步成功、次步失败时不伪造整体成功；重试按当前配置补加入 learner。
        return raftRuntime.removeVoter(endpoint.value())
                .thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom)
                .thenCompose(result -> {
                    if (!result.accepted()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    return raftRuntime.addLearner(endpoint.value())
                            .thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
                });
    }

    /**
     * 按当前角色移除 Raft 成员；已不存在视为重复完成，不改 Broker 路由拓扑。
     */
    public CompletionStage<BrokerConsensusMembershipResult> removeMember(MembershipEndpoint endpoint) {
        RaftMembership membership = raftRuntime.membership();
        if (membership.voters().contains(endpoint.value())) {
            return raftRuntime.removeVoter(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
        }
        if (membership.learners().contains(endpoint.value())) {
            return raftRuntime.removeLearner(endpoint.value())
                    .thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
        }
        return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.duplicate(
                getLeaderEndpoint(), "endpoint is not a consensus member"));
    }

    /**
     * 在日志应用共用锁内检查当前资格并分配本机 Lease 序列，不执行 RPC。
     */
    public LeaseGrantResult grantLease(LeaseGrantRequest request, QuorumRead read) {
        return clusterStateMachine.grantLease(request, read, routingLeaseGrantService);
    }

    /**
     * 触发包含领域状态、operation 和 fence 的本机 Raft 快照。
     */
    public CompletionStage<RaftResult<Void>> createSnapshot() {
        return raftRuntime.snapshot();
    }

    @Override
    public void close() throws InterruptedException {
        raftRuntime.close();
    }

    /**
     * 查询本机稳定身份和动态 Raft 角色；不缓存角色，也不拥有 Runtime 生命周期。
     */
    public static final class LocalBroker {
        private final RaftRuntime<BrokerClusterConsensusCommand, BrokerClusterConsensusResult> runtime;

        private LocalBroker(
                RaftRuntime<BrokerClusterConsensusCommand, BrokerClusterConsensusResult> runtime) {
            this.runtime = runtime;
        }

        public BrokerId getId() {
            return new BrokerId(runtime.nodeId());
        }

        public Optional<LeaderTerm> getLeaderTerm() {
            OptionalLong term = runtime.localLeaderTerm();
            return term.isPresent() ? Optional.of(new LeaderTerm(term.getAsLong())) : Optional.empty();
        }

        public boolean isLeader() {
            return runtime.isLeader();
        }
    }
}
