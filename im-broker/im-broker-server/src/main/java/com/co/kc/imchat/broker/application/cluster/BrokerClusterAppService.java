package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationContext;
import com.co.kc.imchat.broker.support.health.BrokerHealthSample;
import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.authority.model.FenceResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerActivationIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterTopology;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerObservationState;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerDeactivationIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerDrainIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureObservationDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureRemovalIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.DrainGatewayCheckpoint;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.GatewayRegistrationWatermark;
import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmGatewayRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DeactivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DrainBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDeactivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthObservationDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthReportDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerProbeDTO;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerProbeQuery;
import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.BrokerDrainResult;
import com.co.kc.imchat.broker.model.cqrs.result.BrokerFailureRecoveryResult;
import com.co.kc.imchat.broker.model.cqrs.result.DeactivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DrainBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.ForceDeactivateBrokerResult;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.broker.transformer.application.BrokerClusterAppTransformer;
import com.co.kc.imchat.broker.transformer.application.BrokerHealthObservationAppTransformer;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.common.utils.HashUtils;
import com.co.kc.imchat.plugin.lock.core.MutexLock;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;

import static com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase.REBUILDING_ROUTES;

/**
 * Broker 路由控制面应用服务，编排节点激活、停用、排空、故障摘流及单节点 Lease 安装。
 *
 * <p>应用命令在入口转换为领域对象，由聚合决定变更资格和幂等身份。旧 epoch 必须先关闭
 * Lease grant，并取得 fence ACK 或等待 quarantine，才能提交新拓扑；新 ACTIVE 节点取得
 * Lease 后才进入路由重建阶段。Raft learner/voter 成员变更由 BrokerMembershipAppService 负责，
 * 本服务接收 Gateway 重建 ACK，并由 advanceDrain、recoverRoutes 接续排空 cleanup 和操作终态。</p>
 *
 * <p>两条核心流程：拓扑变更执行“领域判断 → 登记操作 → 隔离旧 epoch → 提交拓扑 → 授权新 epoch”；
 * 单节点授权或续租执行“准入 → prepare → quorum → 原子签发 → install”；advanceDrain 等待 ACK、
 * cleanup 与完整证据后完成排空，recoverRoutes 不等待故障节点 cleanup，按证据正常或降级完成。
 * 聚合保护业务规则，
 * Adapter 执行共识与 RPC，并在本机状态机同步边界内协调签发和 fence 应用。</p>
 *
 * <p>一次路由 epoch 切换的执行顺序：</p>
 * <ol>
 *   <li>转换请求并由聚合校验，通过 Raft Begin 登记 operation，尚不发布新拓扑。</li>
 *   <li>通过 quorum ReadIndex 确认领导权，持久化旧 epoch fence，冻结旧 epoch 的 committed ACTIVE Broker 集合，
 *       阻止后续签发旧 Lease；冻结集合不按有效 Lease 的实际安装情况筛选，已经签发的 Lease 此时仍可能有效。</li>
 *   <li>并行请求冻结集合中的 Broker 关闭本地路由准入、等待在途执行结束并持久化 fence，再校验返回的
 *       Broker 身份、epoch、任期和 operationId。</li>
 *   <li>全部 ACK 有效时立即继续；存在失败或无效 ACK 时，在全部请求结束后等待完整
 *       quarantine，使未确认节点的旧 Lease 和协议安全余量失效。</li>
 *   <li>通过 Raft Commit 应用操作并使 resultEpoch 生效；提交未接受时停止，不继续授权。</li>
 *   <li>持久化 AUTHORIZING_NEW_EPOCH，再对新 epoch 的 committed ACTIVE Broker 集合执行 prepare、quorum-backed
 *       Lease 签发和 install；新 epoch 不表示节点新加入，ACTIVE 也不等于已安装有效 Lease；部分节点未确认时保留授权阶段供重试。</li>
 *   <li>全部目标 Broker 确认 Lease 安装后提交 REBUILDING_ROUTES，由后续恢复用例协调 Gateway 快照 ACK、
 *       cleanup 和终态；进入重建阶段不等于 operation 已经成功结束。</li>
 * </ol>
 *
 * <p>异步阶段控制上述先后顺序，不要求请求线程阻塞等待。RPC 请求结束不代表对端停止路由：
 * 有效 ACK 是主动停止证明，quarantine 是无法取得该证明时等待旧 Lease 失效的替代保障。
 * 延迟完成信号由专用 quarantineScheduler 调度，不使用公共线程池。</p>
 *
 * <p>新请求的 epoch 冲突、领域拒绝及操作登记未接受直接返回响应。登记后的推进失败沿异步链传播；
 * 单节点 Lease 的异步失败记录日志并返回 false。部分节点未确认时结束本轮，保留授权阶段供重试。
 * APPLIED/DUPLICATE 表示共识处理状态，本轮完成或进入 REBUILDING_ROUTES 都不等于整个操作已成功结束。</p>
 */
@Slf4j
public final class BrokerClusterAppService {
    private final BrokerRaftAdapter brokerRaftAdapter;
    private final BrokerPeerAdapter brokerPeerAdapter;
    private final GatewayRegistry gatewayRegistry;
    private final Duration quarantineDuration;
    private final Duration maxRouteEventTtl;
    private final ScheduledExecutorService quarantineScheduler;
    private final Duration drainOperationTimeout;
    private final BrokerObservationState observationState;

    private final MutexLock observationLock = new MutexLock();
    private final MutexLock recoveryLock = new MutexLock();
    private final ConcurrentMap<BrokerId, MutexLock> probeLocks = new ConcurrentHashMap<>();

    private final BrokerHealthObservationTracker healthObservationTracker;
    private final BrokerFailureObservationService failureObservationService;

    public BrokerClusterAppService(BrokerRaftAdapter brokerRaftAdapter, BrokerPeerAdapter brokerPeerAdapter,
                                   GatewayRegistry gatewayRegistry, Duration quarantineDuration,
                                   Duration maxRouteEventTtl, ScheduledExecutorService quarantineScheduler,
                                   Duration drainOperationTimeout, BrokerHealthObservationTracker healthObservationTracker,
                                   BrokerFailureObservationService failureObservationService,
                                   Duration observationCooldown) {
        this.brokerRaftAdapter = brokerRaftAdapter;
        this.brokerPeerAdapter = brokerPeerAdapter;
        this.gatewayRegistry = gatewayRegistry;
        this.quarantineDuration = quarantineDuration;
        this.maxRouteEventTtl = maxRouteEventTtl;
        this.quarantineScheduler = quarantineScheduler;
        this.drainOperationTimeout = drainOperationTimeout;
        this.healthObservationTracker = healthObservationTracker;
        this.failureObservationService = failureObservationService;
        this.observationState = new BrokerObservationState(observationCooldown);
    }


    /**
     * 将 READY Broker 纳入路由面，提交拓扑变更并触发新 epoch 授权。
     *
     * <p>新请求的 epoch 冲突和领域拒绝直接返回业务结果，不执行 fence 或共识写入；
     * 历史重试保留原操作身份，按已提交阶段继续隔离和授权。</p>
     * <p>核心步骤：转换输入 → 新请求 epoch 校验 → 聚合激活判断 → 登记操作 → 推进 epoch → 读取最新进度构造响应。</p>
     *
     * @param command 激活目标、期望 epoch 和幂等操作标识
     * @return 保留登记处理状态并映射最新操作 epoch 的响应；推进失败异常结束，不代表 Gateway 重建完成
     */
    public CompletionStage<ActivateBrokerResult> activate(ActivateBrokerCommand command) {
        // 1. 转换基础类型输入，再读取独立的已提交聚合。
        BrokerId brokerId = new BrokerId(command.brokerId());
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        CommandDigest commandDigest = command.digest();

        // 2. 仅对新请求校验 epoch；历史请求交由聚合核对幂等身份。
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        if (!cluster.exists(operationId) && !cluster.epoch().equals(expectedEpoch)) {
            return CompletableFuture.completedFuture(ActivateBrokerResult.conflict(operationId, cluster.epoch()));
        }

        // 3. 聚合冻结变更事实，拒绝时不产生外部副作用。
        BrokerActivationIntent intent = BrokerActivationIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.activate(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(ActivateBrokerResult.failure(operationId, cluster.epoch(), decision));
        }

        // 4. 持久化意图，再按 fence、拓扑提交、Lease 授权顺序推进。
        return brokerRaftAdapter.beginOperation(decision.operation())
                .thenCompose(registered -> {
                    if (!registered.accepted()) {
                        return CompletableFuture.completedFuture(BrokerClusterAppTransformer.INSTANCE.activateBrokerResultFrom(operationId, cluster.epoch(), registered));
                    }
                    // 5. 等待本轮 epoch 推进结束，再读取已提交的操作进度构造响应。
                    return advanceEpoch(registered.operation()).thenApply(ignored -> {
                        BrokerClusterOperation operation = brokerRaftAdapter.findOperation(operationId).orElseThrow();
                        return BrokerClusterAppTransformer.INSTANCE.activateBrokerResultFrom(operationId, cluster.epoch(), registered, operation);
                    });
                });
    }

    /**
     * 将 ACTIVE Broker 停用为 INACTIVE，撤销其在新路由 epoch 中的参与资格。
     *
     * <p>核心步骤：转换输入 → 新请求 epoch 校验 → 聚合停用判断 → 登记操作 → 隔离旧 epoch → 提交并授权新 epoch。</p>
     * <p>聚合保护至少一个 ACTIVE 节点等约束；本用例直接停用，不执行优雅排空的 Gateway ACK 和 cleanup 流程。
     * 历史请求按原操作身份恢复，准入拒绝时不产生共识写入或远端隔离副作用。</p>
     *
     * @param command 停用目标、期望 epoch 和幂等操作标识
     * @return 停用登记状态及最新操作 epoch；部分授权失败保留阶段供重试，推进异常向调用方传播
     */
    public CompletionStage<DeactivateBrokerResult> deactivate(DeactivateBrokerCommand command) {
        BrokerId brokerId = new BrokerId(command.brokerId());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        CommandDigest commandDigest = command.digest();
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());

        // 读取 committed 聚合，业务冲突不作为参数异常抛出。
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        if (!cluster.exists(operationId) && !cluster.epoch().equals(expectedEpoch)) {
            return CompletableFuture.completedFuture(DeactivateBrokerResult.conflict(operationId, cluster.epoch()));
        }

        BrokerDeactivationIntent intent = BrokerDeactivationIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.deactivate(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(DeactivateBrokerResult.failure(operationId, cluster.epoch(), decision));
        }

        // 登记被接受后才执行旧 epoch 隔离及新 epoch 授权。
        return brokerRaftAdapter.beginOperation(decision.operation())
                .thenCompose(registered -> {
                    if (!registered.accepted()) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterAppTransformer.INSTANCE.deactivateBrokerResultFrom(operationId, cluster.epoch(), registered));
                    }
                    // 等待本轮推进结束，读取最新操作阶段并保留登记结果的状态。
                    return advanceEpoch(registered.operation()).thenApply(ignored -> {
                        BrokerClusterOperation operation = brokerRaftAdapter.findOperation(operationId).orElseThrow();
                        return BrokerClusterAppTransformer.INSTANCE.deactivateBrokerResultFrom(operationId, cluster.epoch(), registered, operation);
                    });
                });
    }


    /**
     * 先同步本机采样上下文，再由当前 voter 探测其他 ACTIVE Broker。
     * 每个目标独立互斥；结果归类及上下文重验完成后释放目标锁。
     */
    public CompletionStage<Void> probeFailures() {
        // 1. 读取本机身份、已提交集群和实际成员配置，确定当前 voter 与 ACTIVE 探测目标。
        BrokerId localBrokerId = brokerRaftAdapter.getLocalBroker().getId();
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        ConsensusMembership membership = brokerRaftAdapter.getConsensusMembership();

        Set<BrokerId> voters = cluster.findConsistentVoters(membership);
        List<BrokerNode> brokerNodes = cluster.activeBrokers();

        // 2. 先同步采样上下文；即使本机已不是 voter，也要在上下文变化时清除旧样本。
        BrokerHealthObservationContext context = new BrokerHealthObservationContext(cluster.revision(), cluster.topology(membership));
        healthObservationTracker.synchronize(context);

        // 3. 仅当前 voter 发起探测；其他节点完成上下文同步后直接返回。
        if (!voters.contains(localBrokerId)) {
            return CompletableFuture.completedFuture(null);
        }

        // 4. 排除本机；按目标获取共享锁，已有在途探测时跳过，不等待前一目标完成。
        return brokerNodes.stream()
                .filter(brokerNode -> !brokerNode.id().equals(localBrokerId))
                .map(brokerNode -> {
                    MutexLock probeLock = probeLocks.computeIfAbsent(brokerNode.id(), ignored -> new MutexLock());
                    if (!probeLock.tryLock()) {
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    // 5. 传入本轮领域版本；Adapter 负责请求构造及调用预算，同步异常由阶段承接。
                    return CompletableFuture.<Void>completedFuture(null).thenCompose(ignored ->
                            brokerPeerAdapter.probe(brokerNode, brokerRaftAdapter.getClusterId(), cluster.revision(), cluster.epoch())
                    ).handle((health, failure) -> {
                        // 6. 采用 Adapter 分类并记录采样时刻，不因本机排队耗时重新降级成功结果。
                        BrokerHealthStatus status = failure == null && health != null ? health : BrokerHealthStatus.UNKNOWN;
                        return new BrokerHealthSample(status, MonotonicTimestamp.now());
                    }).thenAccept(sample -> {
                        // 7. 完成后重读集群与成员，仅记录仍属于发起上下文的样本。
                        BrokerCluster completedCluster = brokerRaftAdapter.getClusterSnapshot();
                        ConsensusMembership completedMembership = brokerRaftAdapter.getConsensusMembership();
                        BrokerHealthObservationContext completedContext =
                                new BrokerHealthObservationContext(completedCluster.revision(), completedCluster.topology(completedMembership));
                        if (context.equals(completedContext)) {
                            healthObservationTracker.record(context, brokerNode.id(), sample.status(), sample.completedAt());
                        }
                    }).whenComplete((ignored, failure) -> {
                        // 8. 无论分类、记录成功、丢弃还是异常，都释放捕获的目标锁实例。
                        probeLock.unlock();
                    });
                })
                // 9. 汇合本轮所有探测的完成信号；空目标集合直接完成，不串行化探测。
                .reduce(CompletableFuture.<Void>completedFuture(null),
                        (completed, probeTask) -> completed.thenCombine(probeTask, (ignored, result) -> null));
    }

    /**
     * 协调一轮 Broker 故障处置：优先恢复已登记的故障操作，否则按当前观察尝试自动摘流。
     *
     * <p>仅 Leader 执行处置；存在唯一非终态 FAIL_BROKER 时，沿既有恢复流程继续推进，
     * 不重新要求故障观察满足新建条件。不存在待恢复操作时，读取本机 Leader 任期标记，
     * 重验集群版本并检查成员一致性、冷却期和操作互斥，再汇集本机与其他 voter 的独立观察。
     * 本机任期标记不代表多数派确认；任期不可用时作废冷却并跳过。</p>
     * <p>观察汇合后取得 quorum 领导权证明，重验起始任期、本机角色及完整观察上下文。
     * 领域规则确认新鲜、持续的不可达报告达到当前 voter 多数后，按 Broker ID 稳定顺序
     * 至多尝试登记一个故障摘流操作；聚合或共识拒绝时结束本轮，不继续尝试其他目标。
     * 只有 Begin 被接受才进入既有故障推进流程，该流程独立取得 fencing 所需证明。
     * 本方法不直接执行健康探测，也不移除 Raft voter。</p>
     * <p>观察轮次通过本机锁非阻塞互斥，已有轮次在途时跳过；锁在本轮异步流程实际完成后释放。
     * 单个远端报告失败按空报告处理，不缩小多数分母；其他读取、共识及推进异常通过失败阶段传播。
     * 冷却由观察状态判断，报告新鲜度按汇合后的评估时刻判断；本方法不设整轮耗时上限。
     * 非 Leader、冷却未完成、上下文变化或证据不足时，本轮不发起新操作。</p>
     *
     * @return 本轮协调流程的异步完成信号；正常完成也可能表示跳过或条件不足，
     * 不代表故障摘流成功或所有 Gateway 路由重建完成，后续调度可继续恢复
     */
    public CompletionStage<Void> reconcileFailures() {
        // 1. 非阻塞取得轮次锁；在途轮次实际完成前不启动新一轮。
        if (!observationLock.tryLock()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.<Void>completedFuture(null).thenCompose(unused -> {
            // 2. 仅本机 Leader 协调处置；已有未完成故障操作时优先恢复。
            BrokerCluster initialCluster = brokerRaftAdapter.getClusterSnapshot();
            if (!brokerRaftAdapter.getLocalBroker().isLeader()) {
                observationState.invalidate();
                return CompletableFuture.completedFuture(null);
            }

            Optional<BrokerClusterOperation> unfinished = initialCluster.findUnfinishedFailureOperation();
            if (unfinished.isPresent()) {
                BrokerOperationReconcileCommand command = new BrokerOperationReconcileCommand(unfinished.get().id().value());
                return this.recoverRoutes(command).thenApply(ignored -> (Void) null);
            }

            // 3. 读取本机任期标记并重验 revision；无标记或版本变化时作废冷却。
            LeaderTerm leaderTerm = brokerRaftAdapter.getLocalBroker().getLeaderTerm().orElse(null);
            if (leaderTerm == null) {
                observationState.invalidate();
                return CompletableFuture.completedFuture(null);
            }

            BrokerCluster observedCluster = brokerRaftAdapter.getClusterSnapshot();
            if (!initialCluster.revision().equals(observedCluster.revision())) {
                observationState.invalidate();
                return CompletableFuture.completedFuture(null);
            }

            // 4. 解析一致的 voter 集合，推进本机冷却，并确认没有阻塞新操作的既有操作。
            MonotonicTimestamp observedAt = MonotonicTimestamp.now();
            ConsensusMembership membership = brokerRaftAdapter.getConsensusMembership();
            Set<BrokerId> voterBrokerIds = initialCluster.findConsistentVoters(membership);
            if (CollectionUtils.isEmpty(voterBrokerIds)) {
                observationState.invalidate();
                return CompletableFuture.completedFuture(null);
            }

            BrokerClusterTopology topology = initialCluster.topology(membership);
            if (!observationState.advance(topology, leaderTerm, observedAt) || initialCluster.hasBlockingOperation()) {
                return CompletableFuture.completedFuture(null);
            }

            // 5. 固定采样 Context；先读取本机观察快照，再向其他 voter 拉取报告。
            BrokerHealthObservationContext context = new BrokerHealthObservationContext(initialCluster.revision(), topology);
            healthObservationTracker.synchronize(context);

            Set<BrokerId> brokerIds = FunctionUtils.mappingSet(initialCluster.activeBrokers(), BrokerNode::id);
            List<BrokerHealthObservation> observations = healthObservationTracker.snapshot(context, Instant.now(), MonotonicTimestamp.now());
            // 各请求独立发起，单个失败按空报告处理；汇合时保留本机快照作为初始观察。
            CompletionStage<List<BrokerHealthObservation>> observationStage = voterBrokerIds.stream()
                    .filter(brokerId -> !brokerId.equals(brokerRaftAdapter.getLocalBroker().getId()))
                    .<CompletionStage<List<BrokerHealthObservation>>>map(brokerId -> {
                        BrokerNode brokerNode = initialCluster.findNode(brokerId).orElseThrow();
                        return CompletableFuture.<Void>completedFuture(null)
                                .thenCompose(requestStart ->
                                        brokerPeerAdapter.fetchHealthReport(brokerNode, brokerRaftAdapter.getClusterId(), initialCluster.revision(), initialCluster.epoch(), brokerIds))
                                .exceptionally(failure -> List.of());
                    })
                    .reduce(CompletableFuture.completedFuture(observations),
                            (accumulated, received) -> accumulated.thenCombine(received, ListUtils::union));

            // 6. 汇合后取得同任期 quorum 证明，并重验本机角色及完整 Context。
            return observationStage.thenCompose(collectedObservations ->
                    brokerRaftAdapter.confirmLeadership(new QuorumReadNonce(GeneratorUtils.nextUUID()))
                            .thenCompose(quorumRead -> {
                                BrokerCluster confirmedCluster = brokerRaftAdapter.getClusterSnapshot();
                                ConsensusMembership confirmedMembership = brokerRaftAdapter.getConsensusMembership();
                                // 背景变化则丢弃结果；下一次有效观察重新计时。
                                if (!quorumRead.leaderTerm().equals(leaderTerm)
                                        || !brokerRaftAdapter.getLocalBroker().isLeader()
                                        || !context.equals(new BrokerHealthObservationContext(confirmedCluster.revision(), confirmedCluster.topology(confirmedMembership)))) {
                                    observationState.invalidate();
                                    return CompletableFuture.completedFuture(null);
                                }

                                // 7. 同一时刻评估各目标证据，按 Broker ID 顺序选首个合格目标并交聚合判断。
                                Instant evaluatedAt = Instant.now();
                                for (BrokerNode brokerNode : confirmedCluster.activeBrokers()) {
                                    BrokerFailureObservationDecision observationDecision =
                                            failureObservationService.evaluate(brokerNode.id(), voterBrokerIds, collectedObservations, evaluatedAt);
                                    if (!observationDecision.eligible()) {
                                        continue;
                                    }
                                    ClusterOperationId operationId = ClusterOperationId.forAutomaticFailure(
                                            brokerRaftAdapter.getClusterId(), brokerNode.id(), confirmedCluster.epoch());
                                    BrokerFailureRemovalIntent intent = BrokerFailureRemovalIntent.builder()
                                            .operationId(operationId)
                                            .commandDigest(new CommandDigest(HashUtils.sha256Hex(operationId.value())))
                                            .expectedRevision(confirmedCluster.revision())
                                            .brokerId(brokerNode.id())
                                            .progress(drainProgress())
                                            .changedAt(Instant.now())
                                            .build();
                                    BrokerClusterDecision clusterDecision = confirmedCluster.removeFailedBroker(intent);
                                    if (!clusterDecision.accepted()) {
                                        return CompletableFuture.completedFuture(null);
                                    }

                                    // 8. 以稳定身份提交 Begin；接受后推进故障操作，拒绝则结束本轮。
                                    return brokerRaftAdapter.beginOperation(clusterDecision.operation())
                                            .thenCompose(registrationResult -> {
                                                if (!registrationResult.accepted()) {
                                                    return CompletableFuture.completedFuture(null);
                                                }
                                                log.info("automatic Broker failure accepted operation:{} target:{} epoch:{} reporters:{}",
                                                        operationId.value(), brokerNode.id().value(), confirmedCluster.epoch().value(), observationDecision.observerIds());
                                                return advanceFailureEpoch(registrationResult.operation());
                                            });
                                }
                                return CompletableFuture.completedFuture(null);
                            }));
        }).whenComplete((ignored, failure) -> observationLock.unlock());
    }

    /**
     * 校验健康探测请求的观察背景与调用方资格，并返回本机探测响应。
     *
     * <p>请求必须指向本机、属于同一集群且匹配本机已提交的 revision/epoch；调用方必须属于
     * 实际 Raft 成员配置与 Broker 角色及端点绑定一致的 voter 集合。传输身份认证由前置准入完成，
     * 本方法不以请求中的 callerBrokerId 代替认证身份。</p>
     * <p>本方法只证明本机能在该观察背景下处理请求，不主动探测其他节点、记录健康样本或修改集群，
     * 不执行 quorum 确认，也不要求本机持有路由 Lease。请求不携带绝对期限。</p>
     *
     * @param query 已完成结构校验的探测查询，包含调用方、接收方及集群版本
     * @return 带本机身份及请求关联上下文的响应，不表示整个集群健康或已取得故障处置资格
     * @throws IllegalArgumentException 集群、接收方或版本不匹配，或调用方不是当前一致 voter
     */
    public BrokerProbeDTO probeBroker(BrokerProbeQuery query) {
        // 1. 将边界身份和版本转换为领域值，读取当前已提交集群快照。
        BrokerClusterId probeClusterId = new BrokerClusterId(query.clusterId());
        BrokerId probeCallerId = new BrokerId(query.callerBrokerId());
        BrokerId probeReceiverId = new BrokerId(query.receiverBrokerId());
        ClusterRevision probeRevision = new ClusterRevision(query.revision());
        RoutingEpoch probeEpoch = new RoutingEpoch(query.epoch());
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();

        // 2. 核对请求所属集群、接收者与版本，拒绝错误节点或旧背景的探测。
        if (!brokerRaftAdapter.getClusterId().equals(probeClusterId)
                || !brokerRaftAdapter.getLocalBroker().getId().equals(probeReceiverId)
                || !cluster.revision().equals(probeRevision)
                || !cluster.epoch().equals(probeEpoch)) {
            throw new IllegalArgumentException("observation context mismatch");
        }

        // 3. 按实际成员配置解析一致 voter，确认调用方仍有观察资格。
        ConsensusMembership membership = brokerRaftAdapter.getConsensusMembership();
        Set<BrokerId> voterIds = cluster.findConsistentVoters(membership);
        if (!voterIds.contains(probeCallerId)) {
            throw new IllegalArgumentException("observation caller is not a current voter");
        }
        // 4. 使用本机身份生成响应，不从请求推断健康状态或产生故障票。
        return BrokerHealthObservationAppTransformer.INSTANCE.probeFrom(query, brokerRaftAdapter.getLocalBroker().getId());
    }

    /**
     * 返回本机对指定集群背景下全部 ACTIVE Broker 已积累的健康观察快照。
     *
     * <p>先校验集群、接收者、revision/epoch 及当前 voter 调用资格，再要求查询的 brokerIds
     * 与已提交 ACTIVE 集合完全一致；不提供任意子集查询。本方法读取已有样本，不因收到查询
     * 重新发起探测，也不复用其他观察者的报告。</p>
     * <p>读取前同步 Tracker 的采样 Context，背景变化时按其规则清除旧样本；快照按本机单调时间
     * 判断样本年龄及连续失败窗口，再映射为包含 Duration 的应用 DTO。缺样本、过期或时间回退
     * 按 Tracker 规则返回 UNKNOWN。本方法不修改权威拓扑、执行 quorum 确认或决定自动摘流；
     * 请求身份认证由前置准入负责，不设置绝对请求期限。</p>
     *
     * @param query 已完成结构校验的报告查询，包含调用方、接收方、集群版本及完整被观察 Broker 集合
     * @return 以本机为观察者的报告，携带请求关联上下文及各目标的状态、样本年龄和连续失败时长
     * @throws IllegalArgumentException 观察背景不匹配、调用方不是当前一致 voter，或查询目标集合不完整
     */
    public BrokerHealthReportDTO getHealthReport(BrokerHealthReportQuery query) {
        // 1. 转换请求身份、版本及被观察 Broker 集合。
        BrokerClusterId clusterId = new BrokerClusterId(query.clusterId());
        BrokerId callerBrokerId = new BrokerId(query.callerBrokerId());
        BrokerId receiverBrokerId = new BrokerId(query.receiverBrokerId());
        ClusterRevision revision = new ClusterRevision(query.revision());
        RoutingEpoch epoch = new RoutingEpoch(query.epoch());
        Set<BrokerId> brokerIds = FunctionUtils.mappingSet(query.brokerIds(), BrokerId::new);

        // 2. 读取集群快照并核对请求背景，接收者必须是本机。
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        if (!brokerRaftAdapter.getClusterId().equals(clusterId)
                || !brokerRaftAdapter.getLocalBroker().getId().equals(receiverBrokerId)
                || !cluster.revision().equals(revision)
                || !cluster.epoch().equals(epoch)) {
            throw new IllegalArgumentException("observation context mismatch");
        }

        // 3. 按实际成员配置确认调用方资格，不将任意已注册 Broker 当作 voter。
        ConsensusMembership membership = brokerRaftAdapter.getConsensusMembership();
        Set<BrokerId> voterIds = cluster.findConsistentVoters(membership);
        if (!voterIds.contains(callerBrokerId)) {
            throw new IllegalArgumentException("observation caller is not a current voter");
        }
        // 4. 查询集合须与当前 ACTIVE 集合完全一致，避免用不完整报告表达本轮观察。
        Set<BrokerId> activeBrokerIds = cluster.activeBrokers().stream().map(BrokerNode::id)
                .collect(Collectors.toSet());
        if (!activeBrokerIds.equals(brokerIds)) {
            throw new IllegalArgumentException("report brokerIds do not match committed state");
        }
        // 5. 同步采样 Context，背景变化时由 Tracker 丢弃旧样本。
        BrokerHealthObservationContext context = new BrokerHealthObservationContext(
                cluster.revision(), cluster.topology(membership));
        healthObservationTracker.synchronize(context);

        // 6. 读取本机现有样本快照，转换为应用层状态与时长，不在此处发起探测。
        List<BrokerHealthObservationDTO> observations = healthObservationTracker
                .snapshot(context, Instant.now(), MonotonicTimestamp.now()).stream()
                .map(observation -> BrokerHealthObservationAppTransformer.INSTANCE.healthObservationFrom(observation, Instant.now())).toList();
        // 7. 将观察者固定为本机，保留本次查询的关联上下文。
        return BrokerHealthObservationAppTransformer.INSTANCE.healthReportFrom(query, brokerRaftAdapter.getLocalBroker().getId(), observations);
    }

    /**
     * 在故障场景下摘除 Broker 路由能力，将目标置为 INACTIVE 并启动路由恢复。
     *
     * <p>不等待故障 Broker cleanup，但仍执行旧 epoch 隔离与新 Lease 授权；
     * 后续 Gateway 重建证据由 recoverRoutes 用例协调。</p>
     * <p>核心步骤：转换输入并校验风险确认原因 → 校验新请求 epoch → 冻结恢复水位
     * → 聚合故障摘流判断 → 登记操作 → 隔离旧 epoch → 提交并授权新 epoch。</p>
     * <p>风险确认原因参与命令摘要以识别重试身份，当前不作为独立原因字段写入 operation。
     * 摘流改变节点流量状态，不执行 Raft 成员移除。</p>
     *
     * @param command 故障目标、期望 epoch、操作标识及风险确认原因
     * @return 故障摘流提交结果，不等同于路由重建完成
     */
    public CompletionStage<ForceDeactivateBrokerResult> forceDeactivate(
            ForceDeactivateBrokerCommand command) {
        BrokerId brokerId = new BrokerId(command.brokerId());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        CommandDigest commandDigest = command.digest();
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());

        // 读取 committed 聚合，业务冲突不作为参数异常抛出。
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        if (!cluster.exists(operationId) && !cluster.epoch().equals(expectedEpoch)) {
            return CompletableFuture.completedFuture(ForceDeactivateBrokerResult.conflict(operationId, cluster.epoch()));
        }

        DrainProgress progress = drainProgress();
        BrokerFailureRemovalIntent intent = BrokerFailureRemovalIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .progress(progress)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.removeFailedBroker(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(ForceDeactivateBrokerResult.failure(operationId, cluster.epoch(), decision));
        }

        // 登记故障摘流意图，接受后再执行隔离与新 epoch 授权。
        return brokerRaftAdapter.beginOperation(decision.operation())
                .thenCompose(registered -> {
                    if (!registered.accepted()) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterAppTransformer.INSTANCE.forceDeactivateBrokerResultFrom(operationId, cluster.epoch(), registered));
                    }
                    // 读取本轮推进后的操作进度；路由重建完成证据由恢复用例提交。
                    return advanceFailureEpoch(registered.operation()).thenApply(ignored -> {
                        BrokerClusterOperation operation = brokerRaftAdapter.findOperation(operationId).orElseThrow();
                        return BrokerClusterAppTransformer.INSTANCE.forceDeactivateBrokerResultFrom(operationId, cluster.epoch(), registered, operation);
                    });
                });
    }


    /**
     * 发起 Broker drain，冻结 Gateway 水位并进入旧路由回收流程。
     *
     * <p>本方法只启动排空及新 epoch 授权；Gateway ACK、旧路由 cleanup 和终态提交由排空
     * advanceDrain 用例完成，不在一次 HTTP 请求中等待全部排空。</p>
     * <p>核心步骤：转换输入 → 校验新请求 epoch → 冻结 Gateway 水位与旧事件截止时间 → 聚合排空判断
     * → 登记操作 → 隔离并切换 epoch → 授权剩余 ACTIVE 节点。</p>
     *
     * @param command 排空目标、期望 epoch 和幂等操作标识
     * @return 排空启动结果，不等同于最终 SUCCEEDED
     */
    public CompletionStage<DrainBrokerResult> drain(DrainBrokerCommand command) {
        BrokerId brokerId = new BrokerId(command.brokerId());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        CommandDigest commandDigest = command.digest();
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());

        // 读取 committed 聚合，业务冲突不作为参数异常抛出。
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        if (!cluster.exists(operationId) && !cluster.epoch().equals(expectedEpoch)) {
            return CompletableFuture.completedFuture(DrainBrokerResult.conflict(operationId, cluster.epoch()));
        }

        DrainProgress progress = drainProgress();
        BrokerDrainIntent intent = BrokerDrainIntent.builder()
                .operationId(operationId)
                .commandDigest(commandDigest)
                .expectedRevision(cluster.revision())
                .brokerId(brokerId)
                .progress(progress)
                .changedAt(Instant.now())
                .build();
        BrokerClusterDecision decision = cluster.drain(intent);
        if (!decision.accepted()) {
            return CompletableFuture.completedFuture(DrainBrokerResult.failure(operationId, cluster.epoch(), decision));
        }

        // 持久化排空意图，登记被拒绝时不执行隔离或授权。
        return brokerRaftAdapter.beginOperation(decision.operation())
                .thenCompose(registered -> {
                    if (!registered.accepted()) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterAppTransformer.INSTANCE.drainBrokerResultFrom(operationId, cluster.epoch(), registered));
                    }
                    // 本轮推进结束后读取最新进度；Gateway 排空仍由后续用例完成。
                    return advanceEpoch(registered.operation()).thenApply(ignored -> {
                        BrokerClusterOperation operation = brokerRaftAdapter.findOperation(operationId).orElseThrow();
                        return BrokerClusterAppTransformer.INSTANCE.drainBrokerResultFrom(operationId, cluster.epoch(), registered, operation);
                    });
                });
    }


    /**
     * 推进已登记的 Broker 排空操作，协调路由重建、旧路由清理及排空完成提交。
     *
     * <p>核心步骤：读取已提交操作 → 确认当前阶段 → 等待 Gateway ACK → 按需清理旧路由
     * → 保存或复用恢复证据 → 根据聚合返回的进度等待或提交完成。</p>
     * <p>等待 ACK、操作期限和本轮 cleanup 资格由 operation 判断，清理证据由 DrainProgress 汇总。
     * 重建阶段已有恢复证据时不重复 cleanup，而是提交原证据，触发聚合重新检查旧事件期限、
     * 重建数量、校验结果和路由残留；满足条件后进入 VERIFYING。验证恢复和成功重试直接请求完成，
     * 完成资格及重复提交的幂等性由聚合保证。</p>
     * <p>ACK 未齐全或 cleanup 失败时按操作期限等待重试，超时后尝试提交 FAILED；
     * 失败是否持久化以共识结果为准，不自动降级为成功。证据保存、完成及失败记录的提交异常
     * 直接向上传播，不归类为 cleanup 失败；强制完成由 forceDrain 入口执行。</p>
     *
     * @param command 已登记的 DRAIN_BROKER 操作标识，不在本方法中发起新的排空操作
     * @return 本轮处理结果的异步阶段；返回等待后由后续调度继续推进，不自动等待下一轮条件满足
     * @throws IllegalArgumentException 操作不存在或操作类型不是 DRAIN_BROKER
     */
    public CompletionStage<BrokerDrainResult> advanceDrain(BrokerOperationReconcileCommand command) {
        // 1. 转换操作标识并读取已提交进度，确认本次推进的是排空操作。
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());

        BrokerClusterOperation operation = brokerRaftAdapter.findOperation(operationId)
                .orElseThrow(() -> new IllegalArgumentException("drain operation does not exist"));
        if (operation.type() != BrokerClusterOperationType.DRAIN_BROKER) {
            throw new IllegalArgumentException("operation is not DRAIN_BROKER");
        }

        // 2. 返回已有失败或强制结果；隔离/授权未完成时等待，成功重试继续交给聚合判断幂等。
        BrokerClusterOperationPhase phase = operation.phase();
        if (phase == BrokerClusterOperationPhase.FAILED) {
            return CompletableFuture.completedFuture(BrokerDrainResult.failed(operation.error().value()));
        }
        if (phase == BrokerClusterOperationPhase.FORCED) {
            return CompletableFuture.completedFuture(BrokerDrainResult.forced(operation.error().value()));
        }
        if (!phase.canAdvanceDrain()) {
            return CompletableFuture.completedFuture(BrokerDrainResult.waitingForGateways(
                    "new epoch is not ready for route rebuild"));
        }

        // 3. 重建阶段等待冻结 Gateway 的 ACK；操作期限从创建时间起算，超时后尝试记录失败。
        DrainProgress progress = operation.drainProgress();
        Instant now = Instant.now();
        if (operation.waitingForGatewayAcks(now, drainOperationTimeout)) {
            return CompletableFuture.completedFuture(BrokerDrainResult.waitingForGateways(null));
        }
        if (phase == BrokerClusterOperationPhase.REBUILDING_ROUTES && !progress.allGatewaysConfirmed()) {
            String reason = "Gateway snapshot ACK timeout";
            return brokerRaftAdapter.failDrain(operation, new OperationFailureReason(reason), now)
                    .thenApply(result -> result.accepted() ? BrokerDrainResult.failed(reason) : BrokerDrainResult.waitingForGateways(result.message()));
        }

        // 4. 由操作判断本轮是否需要 cleanup：排空重建、ACK 齐全且无证据；空结果表示跳过 RPC。
        CompletionStage<Optional<RouteCleanupResult>> cleanupStage =
                CompletableFuture.completedFuture(Optional.empty());
        if (operation.needsRouteCleanup()) {
            BrokerNode target = brokerRaftAdapter.getClusterSnapshot().findNode(operation.targetBrokerId())
                    .orElseThrow(() -> new IllegalStateException("drain target is absent"));
            cleanupStage = brokerPeerAdapter.cleanupRoutes(target, operation).thenApply(Optional::of);
        }

        return cleanupStage
                // 5. 后续提交放入独立的内层阶段，使其同步/异步异常都不会进入 cleanup 的异常恢复。
                .<CompletionStage<BrokerDrainResult>>thenApply(cleanup -> CompletableFuture.completedFuture(cleanup)
                        .thenCompose(cleanupResult -> {
                            // 6. 重建阶段提交新证据或原证据，触发聚合重验；验证恢复和成功重试复用已提交操作。
                            CompletionStage<BrokerClusterConsensusResult> evidenceStage =
                                    CompletableFuture.completedFuture(BrokerClusterConsensusResult.applied(operation));
                            if (phase == BrokerClusterOperationPhase.REBUILDING_ROUTES) {
                                RouteRecoveryEvidence recoveryEvidence =
                                        cleanupResult.map(progress::summarizeCleanup).orElse(progress.recoveryEvidence());
                                evidenceStage = brokerRaftAdapter.saveRecoveryEvidence(operation, recoveryEvidence, Instant.now());
                            }

                            return evidenceStage
                                    // 7. 提交未接受则停止，仍在重建则等待；其余请求完成，由聚合重验资格并保证幂等。
                                    .thenCompose(submitted -> {
                                        if (!submitted.accepted()) {
                                            return CompletableFuture.completedFuture(BrokerDrainResult.waitingForCleanup(submitted.message()));
                                        }
                                        if (submitted.operation().phase() == BrokerClusterOperationPhase.REBUILDING_ROUTES) {
                                            return CompletableFuture.completedFuture(BrokerDrainResult.waitingForOldEvents(null));
                                        }
                                        return brokerRaftAdapter.completeDrain(submitted.operation(),
                                                        new ClusterRevision(brokerRaftAdapter.getCommittedState().revision()))
                                                .thenApply(BrokerClusterAppTransformer.INSTANCE::drainCompletionResultFrom);
                                    });
                        }))
                // 8. 仅恢复 cleanup 阶段失败：期限内等待重试，超时后尝试记录 FAILED，不捕获内层提交异常。
                .exceptionally(failure -> {
                    if (failure instanceof CompletionException && failure.getCause() != null) {
                        failure = failure.getCause();
                    }
                    Instant failedAt = Instant.now();
                    if (!operation.timedOut(failedAt, drainOperationTimeout)) {
                        return CompletableFuture.completedFuture(BrokerDrainResult.waitingForCleanup(failure.getMessage()));
                    }
                    String reason = "drain cleanup timeout: " + failure.getMessage();
                    return brokerRaftAdapter.failDrain(operation, new OperationFailureReason(reason), failedAt)
                            .thenApply(result -> result.accepted() ? BrokerDrainResult.failed(reason) : BrokerDrainResult.waitingForCleanup(result.message()));
                })
                // 9. 展开本轮返回的异步阶段；等待结果由后续调度接续，提交异常直接向上传播。
                .thenCompose(submission -> submission);
    }

    /**
     * 管理员在 FAILED 后显式接受残留风险并强制提交 INACTIVE。
     *
     * @param command 排空操作标识与风险确认原因；原因必须非空
     * @return 强制提交结果；只有共识确认 FORCED 才表示操作已强制结束
     */
    public CompletionStage<BrokerDrainResult> forceDrain(ForceBrokerDrainCommand command) {
        AssertUtils.argNotBlank("forced drain reason is required", command.reason());
        BrokerClusterOperation operation = brokerRaftAdapter.findOperation(new ClusterOperationId(command.operationId()))
                .orElseThrow(() -> new IllegalArgumentException("drain operation does not exist"));
        return brokerRaftAdapter.forceDrain(operation, new ClusterRevision(brokerRaftAdapter.getCommittedState().revision()),
                        new OperationFailureReason(command.reason()))
                .thenApply(result -> result.accepted()
                        && result.operation().phase() == BrokerClusterOperationPhase.FORCED
                        ? BrokerDrainResult.forced(command.reason())
                        : BrokerDrainResult.failed(result.message()));
    }

    /**
     * 为指定 ACTIVE Broker 签发并安装指定 epoch 的路由 Lease，使其获得有时限的本地路由执行权。
     *
     * <p>用于新 epoch 提交后的首次授权，以及已有 ACTIVE 节点的周期续租。一次调用只处理一个目标节点；
     * 不修改集群拓扑，也不推进集群 operation 阶段。调用方汇总各节点安装结果后决定阶段推进或重试。</p>
     *
     * <p>核心步骤：准入检查 → prepare → quorum 确认 → 原子签发 → install。</p>
     * <ol>
     *   <li>将命令转换为领域标识，检查请求 epoch、目标 ACTIVE 身份及持久 fence；拒绝时不发起 RPC。</li>
     *   <li>请求目标节点 prepare，记录目标本机的租约申请起点并取得 nonce，将后续 Lease 与该申请绑定。</li>
     *   <li>通过 quorum ReadIndex 确认当前领导权，取得签发所需的 Leader 任期和多数派读取证据。</li>
     *   <li>调用底层原子签发入口，在本机日志应用的同步边界内检查当前聚合资格，并由领域服务
     *       分配序列及生成 Lease。fence 先应用则拒绝签发，无关 revision 更新不阻断签发。</li>
     *   <li>向目标节点发送 install，请其校验并安装 Lease，返回目标节点的接受结果。</li>
     * </ol>
     *
     * <p>资格拒绝、签发拒绝、目标拒绝安装或异步流程异常均表示本轮未完成，返回 false；
     * 异常记录目标和原因，由首次授权恢复流程或后续续租轮次重试。本方法不自行循环重试。</p>
     *
     * @param command 目标 Broker、需要授权的 routing epoch 和关联 operation 标识
     * @return true 表示目标节点已接受本次 Lease 安装；false 表示本轮未完成，不代表整个集群授权结束
     */
    public CompletionStage<Boolean> activateLease(ActivateRoutingLeaseCommand command) {
        // 1. 转换输入并检查准入；epoch、ACTIVE 身份或 fence 不允许时不执行 RPC。
        BrokerId brokerId = new BrokerId(command.brokerId());
        RoutingEpoch epoch = new RoutingEpoch(command.routingEpoch());
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());

        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        if (cluster.checkLeaseGrant(brokerId, epoch).isPresent()) {
            return CompletableFuture.completedFuture(false);
        }

        // 2. 请求目标节点准备安装，取得与本次本地请求绑定的 nonce。
        BrokerNode broker = cluster.findNode(brokerId).orElseThrow();
        return brokerPeerAdapter.prepareLease(broker, epoch, operationId)
                // 3. 将本次 prepare 申请绑定到请求，使用 nonce 确认领导权。
                .thenCompose(nonce -> {
                    LeaseGrantRequest request = new LeaseGrantRequest(brokerId, epoch, nonce);
                    return brokerRaftAdapter.confirmLeadership(QuorumReadNonce.forLease(nonce))
                            // 4. 在底层同步边界内原子检查资格并签发。
                            .thenApply(quorumRead -> brokerRaftAdapter.grantLease(request, quorumRead));
                })
                // 5. 仅安装已签发的 Lease，资格拒绝或签发拒绝时不调用目标节点。
                .thenCompose(grant -> {
                    if (grant.status() != LeaseGrantResult.Status.GRANTED) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return brokerPeerAdapter.installLease(broker, grant.lease());
                })
                // 6. 本轮异步失败交由上层重试，同时保留目标和异常信息供排查。
                .exceptionally(failure -> {
                    log.warn("Routing lease activation failed: brokerId={}, epoch={}, operationId={}",
                            brokerId.value(), epoch.value(), operationId.value(), failure);
                    return false;
                });
    }

    /**
     * 推进已登记故障摘流操作的路由恢复，根据已持久化证据提交正常或降级终态。
     *
     * <p>核心步骤：读取已提交操作 → 等待 Gateway ACK 或确认超时 → 保存或复用恢复证据
     * → 必要时登记降级意图 → 按已持久化意图提交终态。</p>
     * <p>故障节点不执行 cleanup，恢复证据只汇总已确认 Gateway 的重建数量。重建阶段 ACK 齐全时
     * 由聚合检查旧事件期限等完成条件；ACK 未齐全且超过操作期限时，先保存残缺证据，再登记降级原因。
     * VERIFYING 恢复直接使用已持久化原因，不按本轮 ACK 重新选择终态，避免将降级恢复误报为成功。</p>
     * <p>证据或验证提交未接受时停止，提交异常直接向上传播。一次调用只推进当前轮次，
     * 返回等待状态后由后续调度继续处理。</p>
     *
     * @param command 已登记的 FAIL_BROKER 操作标识，不在本方法中发起新的故障摘流
     * @return 本轮等待结果或已确认的正常/降级终态，失败节点不可达不阻塞本流程
     * @throws IllegalArgumentException 操作不存在或操作类型不是 FAIL_BROKER
     */
    public CompletionStage<BrokerFailureRecoveryResult> recoverRoutes(BrokerOperationReconcileCommand command) {
        if (!recoveryLock.tryLock()) {
            return CompletableFuture.completedFuture(BrokerFailureRecoveryResult.waitingForGateways(
                    "failure recovery is already running"));
        }
        try {
            return recoverRoutesInternal(command).whenComplete((result, failure) -> recoveryLock.unlock());
        } catch (RuntimeException failure) {
            recoveryLock.unlock();
            throw failure;
        }
    }

    private CompletionStage<BrokerFailureRecoveryResult> recoverRoutesInternal(BrokerOperationReconcileCommand command) {
        // 1. 转换操作标识，读取已提交进度并确认操作类型。
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        BrokerClusterOperation operation = brokerRaftAdapter.findOperation(operationId)
                .orElseThrow(() -> new IllegalArgumentException("failure recovery operation does not exist"));
        if (operation.type() != BrokerClusterOperationType.FAIL_BROKER) {
            throw new IllegalArgumentException("operation is not FAIL_BROKER");
        }

        // 2. 已有终态直接返回；隔离及授权尚未完成时等待下一轮。
        BrokerClusterOperationPhase phase = operation.phase();
        if (phase == BrokerClusterOperationPhase.SUCCEEDED) {
            return CompletableFuture.completedFuture(BrokerFailureRecoveryResult.succeeded());
        }
        if (phase == BrokerClusterOperationPhase.DEGRADED) {
            return CompletableFuture.completedFuture(BrokerFailureRecoveryResult.degraded(operation.error().value()));
        }
        if (phase == BrokerClusterOperationPhase.FENCING_OLD_EPOCH
                || phase == BrokerClusterOperationPhase.RAFT_COMMITTED
                || phase == BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH) {
            return advanceEpoch(operation).thenCompose(ignored -> {
                BrokerClusterOperation resumed = brokerRaftAdapter.findOperation(operation.id()).orElseThrow();
                if (resumed.phase() == REBUILDING_ROUTES || resumed.phase() == BrokerClusterOperationPhase.VERIFYING) {
                    return recoverRoutesInternal(command);
                }
                return CompletableFuture.completedFuture(BrokerFailureRecoveryResult.waitingForGateways(
                        "new epoch authorization is not complete"));
            });
        }
        if (phase != BrokerClusterOperationPhase.REBUILDING_ROUTES && phase != BrokerClusterOperationPhase.VERIFYING) {
            return CompletableFuture.completedFuture(BrokerFailureRecoveryResult.waitingForGateways(
                    "new epoch is not ready for route rebuild"));
        }

        // 3. 由操作判断是否仍应等待 ACK；最终降级资格在日志应用时重验。
        Instant now = Instant.now();
        if (operation.waitingForGatewayAcks(now, drainOperationTimeout)) {
            return CompletableFuture.completedFuture(BrokerFailureRecoveryResult.waitingForGateways(null));
        }

        // 4. 首次重建保存领域生成的证据；已有证据和验证恢复直接进入后续处理。
        CompletionStage<BrokerClusterConsensusResult> evidenceStage =
                CompletableFuture.completedFuture(BrokerClusterConsensusResult.applied(operation));
        if (phase == BrokerClusterOperationPhase.REBUILDING_ROUTES
                && operation.drainProgress().recoveryEvidence() == null) {
            evidenceStage = brokerRaftAdapter.saveRecoveryEvidence(
                    operation, operation.drainProgress().summarizeRebuild(), now);
        }

        return evidenceStage
                // 5. 证据接受后请求聚合登记完成意图；已经进入 VERIFYING 的操作复用原意图。
                .thenCompose(submitted -> {
                    if (!submitted.accepted() || submitted.operation().phase() == BrokerClusterOperationPhase.VERIFYING) {
                        return CompletableFuture.completedFuture(submitted);
                    }
                    return brokerRaftAdapter.verifyRouteRecovery(
                            submitted.operation(), drainOperationTimeout, now);
                })
                // 6. 只有验证接受才提交完成，正常或降级终态由聚合按持久化事实选择。
                .thenCompose(verified -> {
                    if (!verified.accepted() || verified.operation().phase() != BrokerClusterOperationPhase.VERIFYING) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterAppTransformer.INSTANCE.failureRecoveryResultFrom(verified, operation));
                    }
                    return brokerRaftAdapter.completeRouteRecovery(verified.operation())
                            // 7. 按实际结果转换响应；提交失败时保留本轮已验证阶段，不退回 ACK 等待。
                            .thenApply(completed -> BrokerClusterAppTransformer.INSTANCE.failureRecoveryResultFrom(
                                    completed, verified.operation()));
                });
    }

    /**
     * 确认 Gateway 已完成目标 epoch 的整轮路由重建，并登记排空或故障恢复的确认水位。
     * <p>核心步骤：检查事件期限 → 查找待恢复操作 → 提交 Gateway 确认事实 → 映射共识结果。</p>
     *
     * @param command 已由协议入口转换的整轮路由确认
     * @return 只有共识接受才确认 ACK；无需恢复时返回 NOT_REQUIRED
     */
    public GatewayRouteSnapshotAckResult confirmGatewayRoutes(ConfirmGatewayRoutesCommand command) {
        if (!Instant.now().isBefore(command.updateExpireAt())) {
            return GatewayRouteSnapshotAckResult.rejected("Gateway route snapshot ACK is expired");
        }
        Optional<BrokerClusterOperation> operation =
                brokerRaftAdapter.findActiveRouteRecoveryOperation(new RoutingEpoch(command.routingEpoch()));
        if (operation.isEmpty()
                || operation.get().phase() != BrokerClusterOperationPhase.REBUILDING_ROUTES
                || operation.get().drainProgress().recoveryEvidence() != null) {
            return GatewayRouteSnapshotAckResult.notRequired();
        }
        // 转换领域确认事实，再提交共识；日志应用后才作为已提交恢复证据。
        DrainProgressUpdate confirmation = DrainProgressUpdate.confirmGateway(
                new GatewayId(command.gatewayId()), new RouteRevision(command.routeRevision()),
                new RouteCount(command.routeCount()), new GatewayRouteSnapshotDigest(command.contentChecksum()));
        BrokerClusterConsensusResult result = brokerRaftAdapter.confirmGatewayRoutes(
                        operation.get(), confirmation)
                .toCompletableFuture().join();
        return switch (result.status()) {
            case APPLIED, DUPLICATE -> GatewayRouteSnapshotAckResult.acknowledged();
            case NOT_LEADER -> GatewayRouteSnapshotAckResult.notLeader(result.leaderEndpoint());
            case REJECTED, CONFLICT, UNAVAILABLE -> GatewayRouteSnapshotAckResult.rejected(result.message());
        };
    }

    /**
     * 为排空或故障恢复构造初始证据：读取 Gateway 注册水位、计算旧事件截止时间，初始化待确认状态。
     *
     * <p>核心步骤：读取当前时间 → 读取本机 Gateway 注册表 → 将 lastSeenAt 转为注册水位
     * → 计算旧事件截止时间。证据随操作登记持久化，本方法不等待 ACK，也不执行网络调用。</p>
     *
     * @return 本轮恢复操作的初始 Gateway checkpoint 和旧事件截止时间，尚无 cleanup 摘要
     */
    private DrainProgress drainProgress() {
        Instant currentTime = Instant.now();
        return new DrainProgress(gatewayRegistry.list().stream()
                .map(gateway -> DrainGatewayCheckpoint.pending(
                        gateway.gatewayId(),
                        new GatewayRegistrationWatermark(gateway.lastSeenAt().toEpochMilli())))
                .toList(), currentTime.plus(maxRouteEventTtl), null);
    }

    /**
     * 按已登记操作的阶段推进 epoch 切换：隔离旧 epoch、提交目标拓扑、授权新 epoch。
     *
     * <p>调用方先确认操作登记已被接受。已处于 RAFT_COMMITTED 或 AUTHORIZING_NEW_EPOCH 的操作
     * 从新 epoch 授权步骤恢复；只有 FENCING_OLD_EPOCH 执行以下隔离流程，其他阶段不再推进。</p>
     * <p>核心步骤：quorum 确认 → 恢复或持久化 fence → 并行隔离并聚合 ACK
     * → 全部确认或等满 quarantine → 提交操作使 resultEpoch 生效 → 新 epoch 授权。</p>
     *
     * <ol>
     *   <li>取得 quorum ReadIndex 领导权证据，再读取已提交的隔离事实。同任期复用已有 fence，
     *       换届后持久化更新后的证据；没有对应 fence 时，冻结当前 ACTIVE 节点集合并持久化关闭事实。</li>
     *   <li>并行请求冻结集合中的节点执行本地隔离，等待所有请求结束并汇总有效 ACK。
     *       全部确认即可继续；有节点未确认时，使用专用调度器异步等待完整 quarantine，
     *       让可能残留的旧 Lease 自然失效。RPC 结束本身不代表旧 Lease 已失效。</li>
     *   <li>取得全部停止证明或等满隔离窗口后，才提交目标拓扑；提交被接受后再推进新 epoch 授权。
     *       部分节点授权失败时保留授权阶段供重试，全部授权完成后进入路由重建阶段。</li>
     * </ol>
     *
     * <p>隔离 RPC 失败计为未确认，走 quarantine 路径；领导权确认、隔离事实持久化、调度或
     * 授权阶段提交的异常沿异步链向调用方传播，不在此处转换为业务成功结果。</p>
     *
     * @param operation 已登记且需要推进或恢复的集群操作
     * @return 本轮推进的异步完成信号；部分节点未授权时正常结束并保留授权阶段，提交失败则异常结束
     */
    private CompletionStage<Void> advanceFailureEpoch(BrokerClusterOperation operation) {
        if (!recoveryLock.tryLock()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return advanceEpoch(operation).whenComplete((ignored, failure) -> recoveryLock.unlock());
        } catch (RuntimeException failure) {
            recoveryLock.unlock();
            throw failure;
        }
    }

    private CompletionStage<Void> advanceEpoch(BrokerClusterOperation operation) {
        BrokerClusterOperationPhase phase = operation.phase();
        // 已提交或正在授权的历史请求直接恢复授权。
        if (phase == BrokerClusterOperationPhase.RAFT_COMMITTED
                || phase == BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH) {
            return authorizeCommittedEpoch(operation);
        }
        // 其余非隔离阶段无需推进，后续只处理旧 epoch 隔离。
        if (phase != BrokerClusterOperationPhase.FENCING_OLD_EPOCH) {
            return CompletableFuture.completedFuture(null);
        }

        // 1. 新请求和恢复请求都先取得 quorum 领导权证据。
        return brokerRaftAdapter.confirmLeadership(QuorumReadNonce.forOperation(operation.id()))
                // 2. 读取当前已提交事实，再决定恢复已有 fence 或提交新的关闭事实。
                .thenCompose(quorumRead -> {
                    RoutingEpoch routingEpoch = operation.acceptedEpoch();
                    RoutingEpochFence routingFence = brokerRaftAdapter.findRoutingFence()
                            .filter(fence -> fence.belongsTo(operation.id(), routingEpoch))
                            .orElse(null);
                    if (routingFence != null) {
                        RoutingEpochFence resumedFence = routingFence.resume(quorumRead);
                        // 同任期复用已持久化事实，换届后才提交新的领导权证据。
                        if (resumedFence.equals(routingFence)) {
                            return CompletableFuture.completedFuture(routingFence);
                        }
                        return brokerRaftAdapter.commitRoutingFence(resumedFence);
                    }

                    BrokerClusterState state = brokerRaftAdapter.getCommittedState();
                    RoutingEpochFence fence = RoutingEpochFence.builder()
                            .operationId(operation.id())
                            .epoch(routingEpoch)
                            .leaderTerm(quorumRead.leaderTerm())
                            .quorumReadIndex(quorumRead.readIndex())
                            .fencedBrokerIds(state.activeBrokers().stream().map(node -> new BrokerId(node.brokerId())).toList())
                            .build();
                    // epoch、operation、隔离集合及已有 fence 冲突由聚合应用日志时校验。
                    return brokerRaftAdapter.commitRoutingFence(fence);
                })
                .thenCompose(persistedFence -> persistedFence.fencedBrokerIds().stream()
                        // 3. 发起隔离请求并归类响应；失败、缺失或无效 ACK 都记为未确认。
                        .map(brokerId -> brokerRaftAdapter.getClusterSnapshot().findNode(brokerId)
                                .map(target -> brokerPeerAdapter.fence(target, persistedFence))
                                .orElseGet(() -> CompletableFuture.failedFuture(
                                        new IllegalStateException("fence holder is absent from committed state")))
                                .handle((ack, failure) -> {
                                    // 响应节点身份已由 Adapter 与请求目标核对。
                                    if (failure != null || ack == null || !ack.confirmsFence(persistedFence)) {
                                        return List.<BrokerId>of();
                                    }
                                    return List.of(brokerId);
                                }))
                        // 4. 从空确认集合开始，聚合所有请求的异步结果。
                        .reduce(CompletableFuture.completedFuture(List.of()),
                                // 两个阶段完成后合并确认节点，保留冻结集合顺序。
                                (acknowledged, response) -> acknowledged.thenCombine(response, ListUtils::union))
                        // 5. 汇总未确认节点；全部确认立即返回，否则等满隔离窗口。
                        .thenCompose(acknowledgedBrokerIds -> {
                            List<BrokerId> unconfirmedBrokerIds = persistedFence.fencedBrokerIds().stream()
                                    .filter(brokerId -> !acknowledgedBrokerIds.contains(brokerId))
                                    .toList();
                            if (unconfirmedBrokerIds.isEmpty()) {
                                return CompletableFuture.completedFuture(FenceResult.allAcknowledged(acknowledgedBrokerIds));
                            }

                            // 未取得全部停止证明：专用调度器等满隔离窗口后才允许 Commit。
                            CompletableFuture<FenceResult> quarantineCompleted = new CompletableFuture<>();
                            quarantineScheduler.schedule(() -> {
                                quarantineCompleted.complete(FenceResult.quarantineCompleted(acknowledgedBrokerIds, unconfirmedBrokerIds));
                            }, quarantineDuration.toNanos(), TimeUnit.NANOSECONDS);
                            return quarantineCompleted;
                        }))
                // 6. 隔离条件满足后提交新拓扑，失败时不继续授权。
                .thenCompose(ignored -> brokerRaftAdapter.commitOperation(operation))
                // 7. 按已提交阶段推进新 epoch 的 Lease 授权。
                .thenCompose(committed -> {
                    if (!committed.accepted()) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "failed to commit epoch topology: " + committed.status() + ": " + committed.message()));
                    }
                    return authorizeCommittedEpoch(committed.operation());
                });
    }

    /**
     * 启动或恢复新 epoch 的路由授权，全部 ACTIVE Broker 确认后进入路由重建阶段。
     *
     * <p>核心步骤：读取最新操作 → 准备授权阶段 → 并行安装 Lease → 汇总结果 → 提交重建阶段。</p>
     * <p>已进入 REBUILDING_ROUTES 时直接结束；部分节点未确认时保留授权阶段供重试。
     * 返回值只表示本轮推进结束，不代表整个集群操作已完成。</p>
     * <p>RAFT_COMMITTED 首先通过共识登记 AUTHORIZING_NEW_EPOCH；已在授权阶段的操作直接恢复安装。
     * 本轮只取 resultEpoch 对应聚合的 ACTIVE 节点，等待所有安装响应并汇总 allInstalled。
     * 任一节点未确认都不提交重建阶段；全部确认后才提交 REBUILDING_ROUTES。</p>
     *
     * @param committedOperation 已提交操作的身份来源，实际流程使用最新已提交阶段
     * @return 本轮授权推进的完成信号；非法阶段或阶段提交失败时异常结束
     */
    private CompletionStage<Void> authorizeCommittedEpoch(BrokerClusterOperation committedOperation) {
        // 1. 读取最新操作；已完成授权时不重复安装 Lease。
        BrokerClusterOperation operation = brokerRaftAdapter.findOperation(committedOperation.id())
                .orElseThrow(() -> new IllegalArgumentException("operation does not exist"));
        BrokerClusterOperationPhase phase = operation.phase();
        if (phase == REBUILDING_ROUTES) {
            return CompletableFuture.completedFuture(null);
        }
        if (phase != BrokerClusterOperationPhase.RAFT_COMMITTED
                && phase != BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("operation is not ready for epoch authorization"));
        }

        // 2. 首次授权先提交授权阶段；恢复请求直接使用已登记操作。
        CompletionStage<BrokerClusterOperation> authorizationStage = CompletableFuture.completedFuture(operation);
        if (phase == BrokerClusterOperationPhase.RAFT_COMMITTED) {
            authorizationStage = brokerRaftAdapter.beginEpochAuthorization(operation)
                    .thenApply(submitted -> {
                        if (!submitted.accepted()) {
                            throw new IllegalStateException(
                                    "failed to commit epoch authorization phase: " + submitted.status());
                        }
                        return submitted.operation();
                    });
        }

        return authorizationStage
                // 3. 确定目标 epoch 的 ACTIVE 节点，并行执行 Lease 签发和安装。
                .thenCompose(authorizingOperation -> {
                    BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
                    if (!cluster.epoch().equals(authorizingOperation.resultEpoch())) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "committed state does not match operation target epoch"));
                    }

                    return cluster.activeBrokers().stream()
                            // 安装失败或未接受均视为本轮未完成。
                            .map(broker -> {
                                ActivateRoutingLeaseCommand command = BrokerClusterAppTransformer.INSTANCE.activateRoutingLeaseCommandFrom(authorizingOperation, broker);
                                return activateLease(command).handle((installed, failure) -> failure == null && Boolean.TRUE.equals(installed));
                            })
                            // 4. 等待所有响应，只有每个目标均确认安装才返回 true。
                            .reduce(CompletableFuture.completedFuture(true),
                                    (allInstalled, installation) -> allInstalled.thenCombine(installation, Boolean::logicalAnd));
                })
                // 5. 全部安装请求结束后判断结果；部分未确认则保留授权阶段供重试。
                .thenCompose(allInstalled -> {
                    if (!allInstalled) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // 6. 全部确认后提交重建阶段；操作身份和摘要在阶段推进中保持不变。
                    return brokerRaftAdapter.completeEpochAuthorization(operation)
                            .thenAccept(submitted -> {
                                if (!submitted.accepted()) {
                                    throw new IllegalStateException(
                                            "failed to complete epoch authorization: " + submitted.status());
                                }
                            });
                });
    }

}
