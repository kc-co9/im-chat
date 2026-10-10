package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;

import com.co.kc.imchat.broker.model.cqrs.result.BrokerFailureRecoveryResult;




import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;


import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;

import com.co.kc.imchat.broker.model.cqrs.command.ForceBrokerDrainCommand;

import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;

import com.co.kc.imchat.broker.model.cqrs.result.BrokerDrainResult;

import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.domain.authority.model.FenceResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerActivationIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerDeactivationIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerDrainIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureRemovalIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.DrainGatewayCheckpoint;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.GatewayRegistrationWatermark;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DeactivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DrainBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDeactivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DeactivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DrainBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.ForceDeactivateBrokerResult;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.transformer.application.BrokerClusterAppTransformer;
import com.co.kc.imchat.common.utils.AssertUtils;
import org.apache.commons.collections4.ListUtils;
import lombok.extern.slf4j.Slf4j;

import com.co.kc.imchat.broker.model.cqrs.command.ConfirmGatewayRoutesCommand;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 *   <li>通过 quorum ReadIndex 确认领导权，持久化旧 epoch fence，冻结旧 ACTIVE holder 集合，
 *       阻止后续签发旧 Lease；已经签发的 Lease 此时仍可能有效。</li>
 *   <li>并行请求旧 holder 关闭本地路由准入、等待在途执行结束并持久化 fence，再校验返回的
 *       Broker 身份、epoch、任期和 operationId。</li>
 *   <li>全部 ACK 有效时立即继续；存在失败或无效 ACK 时，在全部请求结束后等待完整
 *       quarantine，使未确认节点的旧 Lease 和协议安全余量失效。</li>
 *   <li>通过 Raft Commit 应用操作并使 resultEpoch 生效；提交未接受时停止，不继续授权。</li>
 *   <li>持久化 AUTHORIZING_NEW_EPOCH，再对新 ACTIVE holder 执行 prepare、quorum-backed
 *       Lease 签发和 install；部分节点未确认时保留授权阶段供重试。</li>
 *   <li>全部 holder 确认后提交 REBUILDING_ROUTES，由后续恢复用例协调 Gateway 快照 ACK、
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
    private final BrokerClusterAdapter brokerClusterAdapter;
    private final GatewayRegistry gatewayRegistry;
    private final Duration quarantineDuration;
    private final Duration maxRouteEventTtl;
    private final ScheduledExecutorService quarantineScheduler;
    private final Duration drainOperationTimeout;

    /**
     * 装配共识提交、旧 epoch 隔离、新 Lease 安装和 Gateway 观察来源，不缓存集群状态。
     *
     * @param brokerClusterAdapter 共识、节点 RPC 与本机原子签发入口
     * @param gatewayRegistry      冻结排空或故障恢复所需的 Gateway 注册水位
     * @param quarantineDuration   无法取得停止证明时等待的隔离窗口
     * @param maxRouteEventTtl      旧路由事件的最大有效期
     * @param quarantineScheduler  配置层管理的专用延迟调度器，本服务不关闭它
     * @param drainOperationTimeout 排空与故障恢复的操作超时
     */
    public BrokerClusterAppService(
            BrokerClusterAdapter brokerClusterAdapter,
            GatewayRegistry gatewayRegistry,
            Duration quarantineDuration,
            Duration maxRouteEventTtl,
            ScheduledExecutorService quarantineScheduler,
            Duration drainOperationTimeout) {
        this.brokerClusterAdapter = brokerClusterAdapter;
        this.gatewayRegistry = gatewayRegistry;
        this.quarantineDuration = quarantineDuration;
        this.maxRouteEventTtl = maxRouteEventTtl;
        this.quarantineScheduler = quarantineScheduler;
        if (drainOperationTimeout == null || drainOperationTimeout.isZero()
                || drainOperationTimeout.isNegative()) {
            throw new IllegalArgumentException("drainOperationTimeout must be positive");
        }
        this.drainOperationTimeout = drainOperationTimeout;
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
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
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
        return brokerClusterAdapter.registerOperation(decision.operation())
                .thenCompose(registered -> {
                    if (!registered.accepted()) {
                        return CompletableFuture.completedFuture(BrokerClusterAppTransformer.INSTANCE.activateBrokerResultFrom(operationId, cluster.epoch(), registered));
                    }
                    // 5. 等待本轮 epoch 推进结束，再读取已提交的操作进度构造响应。
                    return advanceEpoch(registered.operation()).thenApply(ignored -> {
                        BrokerClusterOperation operation = brokerClusterAdapter.findOperation(operationId).orElseThrow();
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
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
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
        return brokerClusterAdapter.registerOperation(decision.operation())
                .thenCompose(registered -> {
                    if (!registered.accepted()) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterAppTransformer.INSTANCE.deactivateBrokerResultFrom(operationId, cluster.epoch(), registered));
                    }
                    // 等待本轮推进结束，读取最新操作阶段并保留登记结果的状态。
                    return advanceEpoch(registered.operation()).thenApply(ignored -> {
                        BrokerClusterOperation operation = brokerClusterAdapter.findOperation(operationId).orElseThrow();
                        return BrokerClusterAppTransformer.INSTANCE.deactivateBrokerResultFrom(operationId, cluster.epoch(), registered, operation);
                    });
                });
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
        AssertUtils.argNotBlank("force reason is required", command.reason());
        CommandDigest commandDigest = command.digest();
        RoutingEpoch expectedEpoch = new RoutingEpoch(command.expectedEpoch());

        // 读取 committed 聚合，业务冲突不作为参数异常抛出。
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
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
        return brokerClusterAdapter.registerOperation(decision.operation())
                .thenCompose(registered -> {
                    if (!registered.accepted()) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterAppTransformer.INSTANCE.forceDeactivateBrokerResultFrom(operationId, cluster.epoch(), registered));
                    }
                    // 读取本轮推进后的操作进度；路由重建完成证据由恢复用例提交。
                    return advanceEpoch(registered.operation()).thenApply(ignored -> {
                        BrokerClusterOperation operation = brokerClusterAdapter.findOperation(operationId).orElseThrow();
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
        BrokerCluster cluster = brokerClusterAdapter.getCluster();
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
        return brokerClusterAdapter.registerOperation(decision.operation())
                .thenCompose(registered -> {
                    if (!registered.accepted()) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterAppTransformer.INSTANCE.drainBrokerResultFrom(operationId, cluster.epoch(), registered));
                    }
                    // 本轮推进结束后读取最新进度；Gateway 排空仍由后续用例完成。
                    return advanceEpoch(registered.operation()).thenApply(ignored -> {
                        BrokerClusterOperation operation = brokerClusterAdapter.findOperation(operationId).orElseThrow();
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

        BrokerClusterOperation operation = brokerClusterAdapter.findOperation(operationId)
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
            return brokerClusterAdapter.failDrain(operation, new OperationFailureReason(reason), now)
                    .thenApply(result -> BrokerDrainResult.failed(result.accepted() ? reason : result.message()));
        }

        // 4. 由操作判断本轮是否需要 cleanup：排空重建、ACK 齐全且无证据；空结果表示跳过 RPC。
        CompletionStage<Optional<RouteCleanupResult>> cleanupStage =
                CompletableFuture.completedFuture(Optional.empty());
        if (operation.needsRouteCleanup()) {
            cleanupStage = brokerClusterAdapter.cleanupRoutes(operation).thenApply(Optional::of);
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
                                evidenceStage = brokerClusterAdapter.saveRecoveryEvidence(operation, recoveryEvidence, Instant.now());
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
                                        return brokerClusterAdapter.completeDrain(submitted.operation(), Instant.now())
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
                    return brokerClusterAdapter.failDrain(operation, new OperationFailureReason(reason), failedAt)
                            .thenApply(result -> BrokerDrainResult.failed(result.accepted() ? reason : result.message()));
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
        BrokerClusterOperation operation = brokerClusterAdapter.findOperation(new ClusterOperationId(command.operationId()))
                .orElseThrow(() -> new IllegalArgumentException("drain operation does not exist"));
        return brokerClusterAdapter.forceDrain(operation, new OperationFailureReason(command.reason()), Instant.now())
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

        BrokerCluster cluster = brokerClusterAdapter.getCluster();
        if (cluster.checkLeaseGrant(brokerId, epoch).isPresent()) {
            return CompletableFuture.completedFuture(false);
        }

        // 2. 请求目标节点准备安装，取得与本次本地请求绑定的 nonce。
        BrokerNode broker = cluster.findNode(brokerId).orElseThrow();
        return brokerClusterAdapter.prepareLease(broker, epoch, operationId)
                // 3. 将本次 prepare 申请绑定到请求，使用 nonce 确认领导权。
                .thenCompose(nonce -> {
                    LeaseGrantRequest request = new LeaseGrantRequest(brokerId, epoch, nonce);
                    return brokerClusterAdapter.confirmLeadership(QuorumReadNonce.forLease(nonce))
                            // 4. 在底层同步边界内原子检查资格并签发。
                            .thenApply(quorumRead -> brokerClusterAdapter.grantLease(request, quorumRead));
                })
                // 5. 仅安装已签发的 Lease，资格拒绝或签发拒绝时不调用目标节点。
                .thenCompose(grant -> {
                    if (grant.status() != LeaseGrantResult.Status.GRANTED) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return brokerClusterAdapter.installLease(broker, grant.lease());
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
        // 1. 转换操作标识，读取已提交进度并确认操作类型。
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        BrokerClusterOperation operation = brokerClusterAdapter.findOperation(operationId)
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
            evidenceStage = brokerClusterAdapter.saveRecoveryEvidence(
                    operation, operation.drainProgress().summarizeRebuild(), now);
        }

        return evidenceStage
                // 5. 证据接受后请求聚合登记完成意图；已经进入 VERIFYING 的操作复用原意图。
                .thenCompose(submitted -> {
                    if (!submitted.accepted() || submitted.operation().phase() == BrokerClusterOperationPhase.VERIFYING) {
                        return CompletableFuture.completedFuture(submitted);
                    }
                    return brokerClusterAdapter.verifyRouteRecovery(
                            submitted.operation(), drainOperationTimeout, now);
                })
                // 6. 只有验证接受才提交完成，正常或降级终态由聚合按持久化事实选择。
                .thenCompose(verified -> {
                    if (!verified.accepted() || verified.operation().phase() != BrokerClusterOperationPhase.VERIFYING) {
                        return CompletableFuture.completedFuture(
                                BrokerClusterAppTransformer.INSTANCE.failureRecoveryResultFrom(verified, operation));
                    }
                    return brokerClusterAdapter.completeRouteRecovery(verified.operation(), Instant.now())
                            // 7. 按实际结果转换响应；提交失败时保留本轮已验证阶段，不退回 ACK 等待。
                            .thenApply(completed -> BrokerClusterAppTransformer.INSTANCE.failureRecoveryResultFrom(
                                    completed, verified.operation()));
                });
    }

    /**
     * 确认 Gateway 已完成目标 epoch 的整轮路由重建，并登记排空或故障恢复的确认水位。
     * <p>核心步骤：检查事件期限 → 查找待恢复操作 → 提交 Gateway 确认事实 → 映射共识结果。</p>
     * @param command 已由协议入口转换的整轮路由确认
     * @return 只有共识接受才确认 ACK；无需恢复时返回 NOT_REQUIRED
     */
    public GatewayRouteSnapshotAckResult confirmGatewayRoutes(ConfirmGatewayRoutesCommand command) {
        if (Instant.now().toEpochMilli() >= command.eventDeadline()) {
            return GatewayRouteSnapshotAckResult.rejected("Gateway route snapshot ACK is expired");
        }
        Optional<BrokerClusterOperation> operation =
                brokerClusterAdapter.findActiveRouteRecoveryOperation(new RoutingEpoch(command.routingEpoch()));
        if (operation.isEmpty()
                || operation.get().phase() != BrokerClusterOperationPhase.REBUILDING_ROUTES
                || operation.get().drainProgress().recoveryEvidence() != null) {
            return GatewayRouteSnapshotAckResult.notRequired();
        }
        // 转换领域确认事实，再提交共识；日志应用后才作为已提交恢复证据。
        DrainProgressUpdate confirmation = DrainProgressUpdate.confirmGateway(
                new GatewayId(command.gatewayId()), new RouteRevision(command.routeRevision()),
                new RouteCount(command.routeCount()), new GatewayRouteSnapshotDigest(command.contentChecksum()));
        BrokerClusterConsensusResult result = brokerClusterAdapter.confirmGatewayRoutes(
                        operation.get(), confirmation, Instant.now())
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
        return brokerClusterAdapter.confirmLeadership(QuorumReadNonce.forOperation(operation.id()))
                // 2. 读取当前已提交事实，再决定恢复已有 fence 或提交新的关闭事实。
                .thenCompose(quorumRead -> {
                    RoutingEpoch routingEpoch = operation.acceptedEpoch();
                    RoutingEpochFence routingFence = brokerClusterAdapter.findRoutingFence()
                            .filter(fence -> fence.belongsTo(operation.id(), routingEpoch))
                            .orElse(null);
                    if (routingFence != null) {
                        RoutingEpochFence resumedFence = routingFence.resume(quorumRead);
                        // 同任期复用已持久化事实，换届后才提交新的领导权证据。
                        if (resumedFence.equals(routingFence)) {
                            return CompletableFuture.completedFuture(routingFence);
                        }
                        return brokerClusterAdapter.persist(resumedFence);
                    }

                    BrokerClusterState state = brokerClusterAdapter.committedState();
                    RoutingEpochFence fence = RoutingEpochFence.builder()
                            .operationId(operation.id())
                            .epoch(routingEpoch)
                            .leaderTerm(quorumRead.leaderTerm())
                            .quorumReadIndex(quorumRead.readIndex())
                            .fencedBrokerIds(state.activeBrokers().stream().map(node -> new BrokerId(node.brokerId())).toList())
                            .build();
                    // epoch、operation、隔离集合及已有 fence 冲突由聚合应用日志时校验。
                    return brokerClusterAdapter.persist(fence);
                })
                .thenCompose(persistedFence -> persistedFence.fencedBrokerIds().stream()
                        // 3. 发起隔离请求并归类响应；失败、缺失或无效 ACK 都记为未确认。
                        .map(brokerId -> brokerClusterAdapter.fenceBroker(brokerId, persistedFence)
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
                .thenCompose(ignored -> brokerClusterAdapter.commitOperation(operation, Instant.now()))
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
        BrokerClusterOperation operation = brokerClusterAdapter.findOperation(committedOperation.id())
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
            authorizationStage = brokerClusterAdapter.startEpochAuthorization(operation, Instant.now())
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
                    BrokerCluster cluster = brokerClusterAdapter.getCluster();
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
                    return brokerClusterAdapter.completeEpochAuthorization(operation, Instant.now())
                            .thenAccept(submitted -> {
                                if (!submitted.accepted()) {
                                    throw new IllegalStateException(
                                            "failed to complete epoch authorization: " + submitted.status());
                                }
                            });
                });
    }


}
