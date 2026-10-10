package com.co.kc.imchat.broker.domain.connection.repository;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionSyncResult;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatch;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatchReception;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteChunkIndex;
import com.co.kc.imchat.broker.domain.connection.model.UserId;

import java.time.Instant;
import java.util.List;

/**
 * 支持 Gateway 分片接收的连接路由 Registry 契约，普通连接存取与完整批次应用使用同一份数据。
 * 接收只返回分片事实，不写入连接表；完整批次由应用服务在 Lease 保护范围内提交。
 */
public interface BatchConnectionRegistry extends ConnectionRegistry {

    /** 收集路由分片，返回未收齐、完整、无效或容量不足的领域结果。 */
    GatewayRouteBatchReception acceptBatch(
            GatewayRouteBatch batch, GatewayRouteChunkIndex chunkIndex, List<UserId> userIds);

    /** 应用完整批次，调用方负责 Lease 保护和事件发布。 */
    ConnectionSyncResult applyBatch(GatewayRouteBatch batch);

    /** 查询指定 Gateway 的实际用户路由，不使用批次输入替代已保存状态。 */
    List<UserId> findGatewayUsers(GatewayId gatewayId);

    /**
     * 按已确认 Gateway 水位清理旧路由，重复请求复用首次删除数量。
     * 调用方负责先核对已提交操作及完整确认集合，本方法不推进集群排空终态。
     */
    RouteCleanupResult cleanupRoutes(
            ClusterOperationId operationId, List<DrainProgressUpdate> confirmations, Instant oldEventDeadline);
}
