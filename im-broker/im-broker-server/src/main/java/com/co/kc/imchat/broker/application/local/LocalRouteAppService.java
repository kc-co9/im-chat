package com.co.kc.imchat.broker.application.local;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.domain.connection.repository.BatchConnectionRegistry;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionSyncResult;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatch;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatchReception;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatchId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteChunkIndex;
import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptGatewayRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CleanupDrainRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import com.co.kc.imchat.broker.sdk.model.result.AcceptGatewayRoutesResult;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelectors;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelector;
import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerDrainTransformer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 维护当前 Broker 的连接路由视图：接收 Gateway 完整路由数据，并按已提交排空水位清理旧路由。
 * 分片暂存由 BatchConnectionRegistry 管理，应用层负责资格检查、Lease 保护和实际路由事件发布。
 * 清理结果缓存与按水位删除由同一 Registry 实现管理，应用层核对已提交操作资格。
 * 两类状态使用不同同步边界，不让清理结果缓存与分片暂存争用服务对象锁。
 * 整轮恢复确认和集群操作终态仍由集群应用服务处理，本服务不提交拓扑变更。
 */
public final class LocalRouteAppService {
    private final String localBrokerId;
    private final BrokerClusterConsensus stateRepository;
    private final LocalRoutingLeaseService leaseService;
    private final BatchConnectionRegistry connectionRegistry;
    private final DomainEventPublisher eventPublisher;
    private final Duration batchTimeout;

    /* 串行完成本轮资格核对、批次收集、Lease 保护应用及事件发布。 */
    private final Object snapshotLock = new Object();
    /* 串行本轮已提交资格核对与清理调用，与分片接收独立。 */
    private final Object cleanupLock = new Object();

    public LocalRouteAppService(
            String localBrokerId, BrokerClusterConsensus stateRepository,
            LocalRoutingLeaseService leaseService, BatchConnectionRegistry connectionRegistry,
            DomainEventPublisher eventPublisher, Duration batchTimeout) {
        this.localBrokerId = localBrokerId;
        this.stateRepository = stateRepository;
        this.leaseService = leaseService;
        this.connectionRegistry = connectionRegistry;
        this.eventPublisher = eventPublisher;
        if (batchTimeout == null || batchTimeout.isZero() || batchTimeout.isNegative()) {
            throw new IllegalArgumentException("batchTimeout must be positive");
        }
        this.batchTimeout = batchTimeout;
    }

    /**
     * 接收 Gateway 的路由分片，收齐并校验完整内容后原子更新本机路由。
     * <p>核心步骤：转换领域输入 → 核对接收资格 → 委托有界批次接收
     * → 完整批次在 Lease 保护下应用并发布实际路由。</p>
     * 同一分片重试不增加暂存字节；批次冲突会释放整批，完整批次在应用前释放暂存容量。
     *
     * @param command 协议入口转换的路由分片命令
     * @return 分片接受、完整应用、路由移动、无效批次或稍后重试结果
     * @throws Exception Lease 执行或应用路由失败时直接传播，由 RPC 边界统一处理
     */
    public AcceptGatewayRoutesResult acceptRoutes(AcceptGatewayRoutesCommand command) throws Exception {
        // 1. 转换领域输入，后续批次判断使用领域对象，不再重复读取协议字段。
        GatewayId gatewayId = new GatewayId(command.gatewayId());
        GatewayRouteBatchId batchId = new GatewayRouteBatchId(gatewayId, command.batchId());
        List<UserId> chunkUsers = command.userIds().stream().map(UserId::new).toList();
        GatewayRouteChunkIndex chunkIndex = new GatewayRouteChunkIndex(command.chunkIndex());

        GatewayRouteBatch incomingBatch = GatewayRouteBatch.builder()
                .batchId(batchId)
                .routingEpoch(new RoutingEpoch(command.routingEpoch()))
                .routeRevision(new RouteRevision(command.routeRevision()))
                .routeBrokerId(new BrokerId(command.routeBrokerId()))
                .chunkCount(command.chunkCount())
                .contentDigest(new GatewayRouteSnapshotDigest(command.contentChecksum()))
                .updateExpiresAt(Instant.ofEpochMilli(command.eventDeadline()))
                .build();

        synchronized (snapshotLock) {
            // 2. 核对期限、已提交代次、接收目标和分片用户的路由归属。
            Instant now = Instant.now();
            BrokerClusterState state = stateRepository.committedState();
            RoutingEpoch committedEpoch = new RoutingEpoch(state.epoch());
            if (!now.isBefore(incomingBatch.updateExpiresAt())) {
                return AcceptGatewayRoutesResult.invalidBatch(state.epoch());
            }
            if (!incomingBatch.routingEpoch().equals(committedEpoch)
                    || !localBrokerId.equals(incomingBatch.routeBrokerId().value())) {
                return AcceptGatewayRoutesResult.routeMoved(state.epoch());
            }
            RouteBrokerSelector selector = RouteBrokerSelectors.forAlgorithm(state.algorithm());
            boolean misplacedUsers = chunkUsers.stream()
                    .anyMatch(userId -> !localBrokerId.equals(selector.select(userId.value(), state).brokerId()));
            if (misplacedUsers) {
                return AcceptGatewayRoutesResult.invalidBatch(state.epoch());
            }

            // 3. Registry 收集分片并返回领域接收事实，未收齐或未接受时不写入路由。
            GatewayRouteBatchReception reception =
                    connectionRegistry.acceptBatch(incomingBatch, chunkIndex, chunkUsers);
            switch (reception.status()) {
                case CHUNK_ACCEPTED:
                    return AcceptGatewayRoutesResult.accepted(state.epoch());
                case INVALID:
                    return AcceptGatewayRoutesResult.invalidBatch(state.epoch());
                case CAPACITY_EXCEEDED:
                    return AcceptGatewayRoutesResult.retryAfter(state.epoch(), batchTimeout.toMillis());
                case COMPLETE:
                    break;
            }

            // 4. 完整批次在 Lease 保护下应用，发布连接表实际状态。
            GatewayRouteBatch batch = reception.batch().orElseThrow();
            AcceptGatewayRoutesResult leaseUnavailable =
                    AcceptGatewayRoutesResult.retryAfter(state.epoch(), batchTimeout.toMillis());
            return leaseService.executeOrElse(committedEpoch, () -> {
                ConnectionSyncResult syncResult = connectionRegistry.applyBatch(batch);
                List<Long> currentUsers = connectionRegistry.findGatewayUsers(gatewayId).stream()
                        .map(UserId::value).toList();
                // 先发布本轮新增连接，再发布连接表中的实际完整路由。
                List<DomainEvent> events = new ArrayList<>(syncResult.added().size() + 1);
                syncResult.added().forEach(connection -> events.add(new ConnectionRegisteredEvent(
                        connection.userId().value(), connection.gatewayId().value())));
                events.add(new ConnectionSyncedEvent(gatewayId.value(), currentUsers, syncResult.removed()));
                eventPublisher.publish(events);
                return AcceptGatewayRoutesResult.applied(state.epoch());
            }, leaseUnavailable);
        }
    }

    /**
     * 按已提交排空证据清理本机旧路由，保留确认水位之后的路由。
     * <p>核心步骤：转换领域输入 → 核对已提交操作与完整水位 → Registry 幂等清理 → 转换本机结果。</p>
     * Registry 保留确认水位之后的路由，重复请求复用首次删除数量；本方法不推进排空终态。
     *
     * @param command 已由协议入口转换的清理命令
     * @return 本机清理结果或明确拒绝，不表示整个排空操作已完成
     */
    public DrainRouteCleanupResult cleanupRoutes(CleanupDrainRoutesCommand command) {
        // 1. 在读取状态前转换领域标识及 Gateway 确认水位，事件期限保持协议毫秒精度。
        ClusterOperationId operationId = new ClusterOperationId(command.operationId());
        BrokerId brokerId = new BrokerId(localBrokerId);
        RoutingEpoch oldEpoch = new RoutingEpoch(command.oldEpoch());
        RoutingEpoch newEpoch = new RoutingEpoch(command.newEpoch());
        Instant oldEventDeadline = Instant.ofEpochMilli(command.eventDeadline());
        List<DrainProgressUpdate> confirmations = command.gateways().stream()
                .map(watermark -> DrainProgressUpdate.confirmGateway(
                        new GatewayId(watermark.gatewayId()), new RouteRevision(watermark.routeRevision()),
                        new RouteCount(watermark.routeCount()), new GatewayRouteSnapshotDigest(watermark.contentChecksum())))
                .toList();

        synchronized (cleanupLock) {
            // 2. 本轮只读取一次已提交操作，领域对象核对目标、阶段、代次、期限和完整水位。
            BrokerClusterOperation operation = stateRepository.findOperation(operationId).orElse(null);
            if (operation == null || !operation.matchesRouteCleanup(
                    brokerId, oldEpoch, newEpoch, oldEventDeadline, confirmations)) {
                return DrainRouteCleanupResult.rejected(connectionRegistry.count());
            }

            // 3. Registry 负责按水位删除、复用首次结果及有界缓存，不在应用层展开存储策略。
            RouteCleanupResult cleanup = connectionRegistry.cleanupRoutes(operationId, confirmations, oldEventDeadline);
            // 4. 转换本机清理事实，不将其解释为整个排空已经完成。
            return BrokerDrainTransformer.INSTANCE.drainRouteCleanupResultFrom(cleanup);
        }
    }

}
