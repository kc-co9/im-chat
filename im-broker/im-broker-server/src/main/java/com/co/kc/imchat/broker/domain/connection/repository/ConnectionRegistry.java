package com.co.kc.imchat.broker.domain.connection.repository;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionSyncResult;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.domain.connection.model.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Broker 本机用户到 Gateway 的路由存取契约。
 * 注册、注销和完整快照统一携带版本证据；查询返回同一领域实体，不维护独立无版本投影。
 */
public interface ConnectionRegistry {
    /** 按注册实体的 revision 和有效期限应用更新，重复或旧版本返回 false。 */
    boolean register(ConnectionRegistration registration);

    /** 按删除更新的版本证据注销并保留 tombstone，禁止无版本删除。 */
    boolean unregister(ConnectionRouteId id, RouteRevision revision, Instant updateExpiresAt);

    /** 查询用户当前活跃注册，包括版本、水位和观察时间，不触发跨 Broker 查询。 */
    List<ConnectionRegistration> find(UserId userId);

    /** 返回本机全部活跃注册，不包含 tombstone。 */
    List<ConnectionRegistration> list();

    /** 统计本机活跃用户到 Gateway 路由。 */
    long count();

    /**
     * 按 Gateway 在指定 revision 上报的完整用户集合原子对账。
     * 输入可由分片汇总而来；保留 revision 更高的增量路由，同 revision 的内容一致性由聚合校验。
     */
    ConnectionSyncResult reconcileRoutes(
            GatewayId gatewayId, RouteRevision revision, Instant updateExpiresAt, List<UserId> users);

    /** 回收已覆盖且超过更新期限和宽限期的删除标记。 */
    int collectExpiredTombstones(Duration gracePeriod);

    /** Gateway 整体移除时清理其路由和版本状态，不伪造逐连接注销版本。 */
    ConnectionSyncResult removeGateway(GatewayId gatewayId);

    /** 只删除确认 revision 覆盖的旧路由并保留 tombstone。 */
    ConnectionSyncResult purgeRoutes(GatewayId gatewayId, RouteRevision revision, Instant oldEventDeadline);
}
