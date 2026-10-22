package com.co.kc.imchat.broker.infrastructure.domain.connection;

import com.co.kc.imchat.broker.domain.connection.repository.BatchConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionSyncResult;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatch;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatchId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatchReception;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteChunkIndex;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.domain.connection.model.RouteCleanupId;
import com.co.kc.imchat.broker.domain.connection.model.RouteCleanupCheckpoint;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.alicp.jetcache.Cache;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;

/**
 * 为连接路由 Registry 增加有界分片暂存能力，收齐且摘要正确后才允许应用完整批次。
 * GatewayRouteBatch 负责单批次一致性，本对象负责跨批次容量、回收和接收结果。
 * 批次缓存、容量和回收属于本机实现；普通连接操作委托底层存储，应用服务负责 Lease 和发布。
 */
public final class InMemoryBatchConnectionRegistry implements BatchConnectionRegistry {
    /* 普通连接操作与完整批次应用共用的底层路由存储。 */
    private final ConnectionRegistry connectionRegistry;
    /* 直接读取接收容量与保留期限，不复制配置标量。 */
    private final BrokerRoutingProperties properties;

    /* 未收齐批次的 TTL 和数量上限由 JetCache 管理，接收方法保护收集与容量判定。 */
    private final Cache<GatewayRouteBatchId, GatewayRouteBatch> receivingBatches;
    /* 清理检查点由 JetCache 控制容量，本机重启后失效，不承担持久化幂等保证。 */
    private final Cache<RouteCleanupId, RouteCleanupCheckpoint> completedCleanups;

    /* 清理与分片接收独立互斥，不阻塞另一类请求。 */
    private final Object cleanupLock = new Object();

    public InMemoryBatchConnectionRegistry(ConnectionRegistry connectionRegistry, BrokerRoutingProperties properties,
            Cache<GatewayRouteBatchId, GatewayRouteBatch> receivingBatches,
            Cache<RouteCleanupId, RouteCleanupCheckpoint> completedCleanups) {
        this.connectionRegistry = connectionRegistry;
        this.properties = properties;
        this.receivingBatches = receivingBatches;
        this.completedCleanups = completedCleanups;
    }

    /**
     * 回收超时批次、检查容量并收集分片；完整且有效时返回可应用批次，不自行写入路由。
     * 完整或冲突批次释放暂存容量，相同分片重试不重复计费。
     */
    @Override
    public synchronized GatewayRouteBatchReception acceptBatch(
            GatewayRouteBatch incoming, GatewayRouteChunkIndex chunkIndex, List<UserId> userIds) {
        // 1. JetCache 回收超时批次；冲突时释放整批，不混用元数据或重复分片内容。
        GatewayRouteBatch batch = receivingBatches.get(incoming.batchId());
        if (batch != null && !batch.accepts(incoming, chunkIndex, userIds)) {
            receivingBatches.remove(batch.batchId());
            return new GatewayRouteBatchReception(GatewayRouteBatchReception.Status.INVALID, Optional.empty());
        }

        // 2. 只为新批次、新分片检查容量；JetCache 管理数量上限，字节按有效批次计算。
        boolean newBatch = batch == null;
        boolean newChunk = newBatch || !batch.containsChunk(chunkIndex);
        long chunkBytes = Math.multiplyExact((long) userIds.size(), Long.BYTES);
        List<GatewayRouteBatch> pending = JetCacheFactory.localValues(receivingBatches);
        if (newBatch && pending.size() >= properties.getMaxPendingSnapshotBatches()) {
            return new GatewayRouteBatchReception(GatewayRouteBatchReception.Status.CAPACITY_EXCEEDED, Optional.empty());
        }
        long pendingBytes = pending.stream().mapToLong(GatewayRouteBatch::bytes).sum();
        if (newChunk && pendingBytes + chunkBytes > properties.getMaxPendingSnapshotBytes()) {
            return new GatewayRouteBatchReception(GatewayRouteBatchReception.Status.CAPACITY_EXCEEDED, Optional.empty());
        }
        if (newBatch) {
            batch = incoming;
        }
        batch.collect(incoming, chunkIndex, userIds);
        if (!batch.complete()) {
            // 仅首次写入：后续分片修改同一个本地对象，不刷新 JetCache 的写入 TTL。
            if (newBatch) {
                receivingBatches.put(batch.batchId(), batch);
            }
            return new GatewayRouteBatchReception(GatewayRouteBatchReception.Status.CHUNK_ACCEPTED, Optional.empty());
        }

        // 3. 收齐即释放暂存；只有摘要正确的完整批次可用于更新实际连接表。
        if (!newBatch) {
            receivingBatches.remove(batch.batchId());
        }
        if (!batch.contentVerified()) {
            return new GatewayRouteBatchReception(GatewayRouteBatchReception.Status.INVALID, Optional.empty());
        }
        return new GatewayRouteBatchReception(GatewayRouteBatchReception.Status.COMPLETE, Optional.of(batch));
    }

    /** 将完整批次写入实际连接表，必须由调用方在 Lease 保护范围内执行。 */
    @Override
    public ConnectionSyncResult applyBatch(GatewayRouteBatch batch) {
        return connectionRegistry.reconcileRoutes(batch.batchId().gatewayId(),
                batch.routeRevision(), batch.updateExpireAt(), batch.orderedUsers());
    }

    /** 读取实际连接表中的 Gateway 用户，旧批次不会回退发布的观察结果。 */
    @Override
    public List<UserId> findGatewayUsers(GatewayId gatewayId) {
        return connectionRegistry.list().stream()
                .filter(route -> route.gatewayId().equals(gatewayId))
                .map(ConnectionRegistration::userId).sorted(Comparator.comparing(UserId::value)).toList();
    }

    /**
     * 逐 Gateway 复用已完成清理或按水位删除，登记首次结果后汇总本机残留。
     * 幂等缓存有容量上界且不持久化；集群操作资格由应用服务预先核对。
     */
    @Override
    public RouteCleanupResult cleanupRoutes(
            ClusterOperationId operationId, List<DrainProgressUpdate> confirmations, Instant oldEventExpireAt) {
        synchronized (cleanupLock) {
            RouteCount removedRoutes = RouteCount.ZERO;
            for (DrainProgressUpdate confirmation : confirmations) {
                // 1. 重试水位必须一致，复用首次删除数量，不重复修改存储。
                RouteCleanupId key = new RouteCleanupId(operationId, confirmation.gatewayId());
                RouteCleanupCheckpoint existing = completedCleanups.get(key);
                if (existing != null) {
                    existing.checkRevision(confirmation.routeRevision());
                    removedRoutes = removedRoutes.plus(existing.removedRouteCount());
                    continue;
                }

                // 2. 只删除确认水位覆盖的旧路由，记录本轮删除事实。
                ConnectionSyncResult changes = connectionRegistry.purgeRoutes(
                        confirmation.gatewayId(), confirmation.routeRevision(), oldEventExpireAt);
                RouteCount removedCount = new RouteCount((long) changes.removed().size());
                completedCleanups.put(key, new RouteCleanupCheckpoint(confirmation.routeRevision(), removedCount));
                removedRoutes = removedRoutes.plus(removedCount);
            }
            return new RouteCleanupResult(removedRoutes, new RouteCount(connectionRegistry.count()), true);
        }
    }
    @Override
    public boolean register(ConnectionRegistration registration) {
        return connectionRegistry.register(registration);
    }

    @Override
    public boolean unregister(ConnectionRouteId id, RouteRevision revision, Instant updateExpireAt) {
        return connectionRegistry.unregister(id, revision, updateExpireAt);
    }

    @Override
    public List<ConnectionRegistration> find(UserId userId) {
        return connectionRegistry.find(userId);
    }

    @Override
    public List<ConnectionRegistration> list() {
        return connectionRegistry.list();
    }

    @Override
    public long count() {
        return connectionRegistry.count();
    }

    @Override
    public ConnectionSyncResult reconcileRoutes(
            GatewayId gatewayId, RouteRevision routeRevision, Instant updateExpireAt, List<UserId> userIds) {
        return connectionRegistry.reconcileRoutes(gatewayId, routeRevision, updateExpireAt, userIds);
    }

    @Override
    public int collectExpiredTombstones(Duration gracePeriod) {
        return connectionRegistry.collectExpiredTombstones(gracePeriod);
    }

    @Override
    public ConnectionSyncResult removeGateway(GatewayId gatewayId) {
        return connectionRegistry.removeGateway(gatewayId);
    }

    @Override
    public ConnectionSyncResult purgeRoutes(GatewayId gatewayId, RouteRevision confirmedRouteRevision, Instant oldEventExpireAt) {
        return connectionRegistry.purgeRoutes(gatewayId, confirmedRouteRevision, oldEventExpireAt);
    }

}
