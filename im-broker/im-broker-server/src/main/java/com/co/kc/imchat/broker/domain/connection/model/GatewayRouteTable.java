package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.domain.user.model.UserId;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.common.utils.HashUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;

/**
 * 一个 Gateway 在当前 RouteBroker 上的连接路由聚合根。
 *
 * <p>聚合根以 gatewayId 划分一致性边界，统一保护 route revision 单调性、完整快照原子应用、同 revision
 * 内容绑定以及 tombstone 生命周期。</p>
 *
 * <p>聚合本身不提供线程安全保证；持有它的 Registry 必须串行化每次注册、注销、快照应用、排空清理和
 * tombstone 回收操作，保证这些多步状态变更作为一个完整聚合行为执行。</p>
 */
public final class GatewayRouteTable {
    /**
     * 聚合身份，对应一个 Gateway 进程实例。
     */
    private final GatewayId gatewayId;
    /**
     * 聚合内连接路由实体，按用户身份建立内部索引。该 Map 是聚合私有状态，不作为并发容器使用。
     */
    private final Map<UserId, ConnectionRegistration> routes = new LinkedHashMap<>();
    /**
     * 最近一次已接受的完整快照 revision 与用户集合摘要，不保存快照中的用户列表。
     * 为空表示尚未接受过完整快照；用于忽略旧快照、识别同 revision 的幂等重试，
     * 并拒绝相同 revision 但内容不同的快照。
     */
    private GatewayRouteSnapshotCheckpoint snapshotCheckpoint;

    public GatewayRouteTable(GatewayId gatewayId) {
        AssertUtils.domainPropNotNull("gatewayId must not be null", gatewayId);
        this.gatewayId = gatewayId;
    }

    public Optional<ConnectionRegistration> find(UserId userId) {
        return Optional.ofNullable(routes.get(userId));
    }

    /** 返回当前活跃注册；删除标记仍保留在聚合内，不参与投递或诊断位置查询。 */
    public List<ConnectionRegistration> registrations() {
        return routes.values().stream().filter(ConnectionRegistration::active).toList();
    }

    public ConnectionRouteChangeSet register(ConnectionRegistration registration) {
        AssertUtils.domainPropTrue("registration belongs to another Gateway", gatewayId.equals(registration.gatewayId()));
        AssertUtils.domainPropTrue("registration must be active", registration.active());
        UserId userId = registration.userId();
        RouteRevision revision = registration.revision();
        Instant updateExpireAt = registration.updateExpireAt();
        Instant now = Instant.now();
        AssertUtils.allDomainPropNotNull("route update is incomplete", userId, revision, updateExpireAt);
        AssertUtils.domainPropTrue("route update has expired", updateExpireAt.isAfter(now));

        ConnectionRegistration connectionRoute = routes.get(userId);
        if (connectionRoute != null && connectionRoute.hasRevisionAtLeast(revision)) {
            return ConnectionRouteChangeSet.unchanged();
        }

        ConnectionRouteId id = new ConnectionRouteId(userId, gatewayId);
        ConnectionRegistration updated;
        if (connectionRoute == null) {
            updated = registration;
        } else {
            updated = connectionRoute.acceptActiveUpdate(revision, updateExpireAt);
        }
        routes.put(userId, updated);

        if (connectionRoute != null && connectionRoute.active()) {
            return new ConnectionRouteChangeSet(true, List.of(), List.of());
        } else {
            return new ConnectionRouteChangeSet(true, List.of(id), List.of());
        }
    }

    public ConnectionRouteChangeSet unregister(UserId userId, RouteRevision revision, Instant updateExpireAt) {
        Instant now = Instant.now();
        AssertUtils.allDomainPropNotNull("route update is incomplete", userId, revision, updateExpireAt);
        AssertUtils.domainPropTrue("route update has expired", updateExpireAt.isAfter(now));

        ConnectionRegistration connectionRoute = routes.get(userId);
        if (connectionRoute != null && connectionRoute.hasRevisionAtLeast(revision)) {
            return ConnectionRouteChangeSet.unchanged();
        }

        ConnectionRouteId id = new ConnectionRouteId(userId, gatewayId);
        ConnectionRegistration updated;
        if (connectionRoute == null) {
            updated = ConnectionRegistration.tombstone(id, revision, updateExpireAt, now);
        } else {
            updated = connectionRoute.acceptRemovalUpdate(revision, updateExpireAt, now);
        }
        routes.put(userId, updated);

        if (connectionRoute != null && connectionRoute.active()) {
            return new ConnectionRouteChangeSet(true, List.of(), List.of(id));
        } else {
            return new ConnectionRouteChangeSet(true, List.of(), List.of());
        }
    }

    /**
     * 将此 Gateway 在当前 RouteBroker 上的完整用户路由快照与聚合状态原子对账。
     *
     * <ol>
     *     <li>校验快照 revision 与有效截止时刻。</li>
     *     <li>对用户集合去重、排序并计算稳定摘要。</li>
     *     <li>与已接受的快照检查点比较：忽略旧 revision；将同 revision、同摘要视为幂等重试；
     *     拒绝同 revision、不同摘要的协议冲突。</li>
     *     <li>将快照中的用户恢复为 ACTIVE，但不覆盖 revision 更高的增量路由状态。</li>
     *     <li>将快照中缺失且 revision 不高于快照的旧 ACTIVE 路由转为 tombstone。</li>
     *     <li>保存新的快照检查点，并返回外部连接投影需要新增或移除的路由。</li>
     * </ol>
     *
     * @param userIds         快照声明的完整用户集合；为空表示该 Gateway 当前没有用户路由
     * @param revision        快照覆盖到的 Gateway 路由修订号
     * @param updateExpireAt 本次快照更新的有效截止时刻
     * @return 快照是否被接受，以及外部连接投影需要新增或移除的路由
     */
    public ConnectionRouteChangeSet reconcile(List<UserId> userIds,
                                              RouteRevision revision,
                                              Instant updateExpireAt) {
        // 1. 校验快照元数据和有效期。
        Instant now = Instant.now();
        AssertUtils.allDomainPropNotNull("route snapshot is incomplete", revision, updateExpireAt);
        AssertUtils.domainPropTrue("route snapshot has expired", updateExpireAt.isAfter(now));

        // 2. 规范化用户集合，并为快照内容生成稳定摘要。
        List<UserId> snapshotUsers = CollectionUtils.emptyIfNull(userIds)
                .stream().distinct()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(UserId::value))
                .toList();
        GatewayRouteSnapshotDigest snapshotDigest = new GatewayRouteSnapshotDigest(
                HashUtils.sha256HexLongs(FunctionUtils.mappingList(snapshotUsers, UserId::value)));

        // 3. 根据已接受的检查点识别旧快照、幂等重试和内容冲突。
        if (snapshotCheckpoint != null && revision.compareTo(snapshotCheckpoint.revision()) < 0) {
            return ConnectionRouteChangeSet.unchanged();
        }
        if (snapshotCheckpoint != null && revision.equals(snapshotCheckpoint.revision())) {
            if (!snapshotDigest.equals(snapshotCheckpoint.snapshotDigest())) {
                throw new IllegalArgumentException("snapshot revision is already bound to different content");
            }
            return ConnectionRouteChangeSet.unchanged();
        }

        List<ConnectionRouteId> added = new ArrayList<>();
        List<ConnectionRouteId> removed = new ArrayList<>();

        // 4. 将快照中的用户路由更新为 ACTIVE，保留 revision 更高的增量状态。
        for (UserId userId : snapshotUsers) {
            ConnectionRegistration connectionRoute = routes.get(userId);
            if (connectionRoute != null && connectionRoute.supersedes(revision)) {
                continue;
            }
            ConnectionRouteId id = new ConnectionRouteId(userId, gatewayId);
            if (connectionRoute == null || !connectionRoute.active()) {
                added.add(id);
            }
            ConnectionRegistration updated;
            if (connectionRoute == null) {
                updated = ConnectionRegistration.active(id, revision, updateExpireAt);
            } else {
                updated = connectionRoute.acceptActiveUpdate(revision, updateExpireAt);
            }
            routes.put(userId, updated);
        }

        // 5. 将快照中缺失且未超过快照 revision 的路由转换为 tombstone。
        for (UserId userId : routes.keySet()) {
            if (snapshotUsers.contains(userId)) {
                continue;
            }
            ConnectionRegistration connectionRoute = routes.get(userId);
            if (connectionRoute.supersedes(revision)) {
                continue;
            }
            ConnectionRouteId id = connectionRoute.id();
            if (connectionRoute.active()) {
                removed.add(id);
            }
            routes.put(userId, connectionRoute.acceptSnapshotRemoval(
                    revision, updateExpireAt, now));
        }

        // 6. 保存新检查点，并返回外部连接投影的增删结果。
        this.snapshotCheckpoint = new GatewayRouteSnapshotCheckpoint(revision, snapshotDigest);

        return new ConnectionRouteChangeSet(true, added, removed);
    }

    public ConnectionRouteChangeSet purge(RouteRevision routeRevision,
                                          Instant updateExpireAt) {
        AssertUtils.domainPropNotNull("drain cleanup deadline is required", updateExpireAt);
        AssertUtils.domainPropNotNull("drain cleanup watermark is required", routeRevision);
        Instant now = Instant.now();
        List<ConnectionRouteId> removed = new ArrayList<>();
        for (Map.Entry<UserId, ConnectionRegistration> entry : routes.entrySet()) {
            ConnectionRegistration connectionRoute = entry.getValue();
            if (!connectionRoute.canPurgeAt(routeRevision)) {
                continue;
            }
            removed.add(connectionRoute.id());
            entry.setValue(connectionRoute.purgeAt(routeRevision, updateExpireAt, now));
        }
        return new ConnectionRouteChangeSet(true, List.of(), removed);
    }

    public int collectExpiredTombstones(Duration gracePeriod) {
        AssertUtils.domainPropNotNull("tombstone collection grace period is required", gracePeriod);
        AssertUtils.domainPropTrue("grace period must not be negative", !gracePeriod.isNegative());
        Instant now = Instant.now();
        int before = routes.size();
        routes.entrySet().removeIf(entry -> entry.getValue().canCollect(now, gracePeriod));
        return before - routes.size();
    }


}
