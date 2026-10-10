package com.co.kc.imchat.broker.infrastructure.domain.connection;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteChangeSet;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionSyncResult;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteTable;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.domain.connection.model.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 本机连接路由内存存储，以 GatewayRouteTable 为唯一记录来源。
 * 同步保护聚合行为，不另建用户/Gateway 注册投影；查询不补建记录或改变版本。
 */
public class InMemoryConnectionRegistry implements ConnectionRegistry {
    /* 各 Gateway 的版本化注册及 tombstone，所有访问由本对象同步保护。 */
    private final Map<GatewayId, GatewayRouteTable> routeTables = new LinkedHashMap<>();
    /* 只索引活跃身份，不复制实体字段，按用户查询无需扫描所有 Gateway 聚合。 */
    private final Map<UserId, Set<GatewayId>> userGateways = new LinkedHashMap<>();

    @Override
    public synchronized boolean register(ConnectionRegistration registration) {
        GatewayRouteTable table = routeTables.computeIfAbsent(registration.gatewayId(), GatewayRouteTable::new);
        ConnectionRouteChangeSet changes = table.register(registration);
        indexChanges(changes);
        return changes.accepted();
    }

    @Override
    public synchronized boolean unregister(ConnectionRouteId id, RouteRevision revision, Instant updateExpiresAt) {
        GatewayRouteTable table = routeTables.computeIfAbsent(id.gatewayId(), GatewayRouteTable::new);
        ConnectionRouteChangeSet changes = table.unregister(id.userId(), revision, updateExpiresAt);
        indexChanges(changes);
        return changes.accepted();
    }

    @Override
    public synchronized List<ConnectionRegistration> find(UserId userId) {
        return userGateways.getOrDefault(userId, Set.of()).stream()
                .map(gatewayId -> routeTables.get(gatewayId).find(userId).orElseThrow()).toList();
    }

    @Override
    public synchronized List<ConnectionRegistration> list() {
        return routeTables.values().stream().flatMap(table -> table.registrations().stream()).toList();
    }

    @Override
    public synchronized long count() {
        return userGateways.values().stream().mapToLong(Set::size).sum();
    }

    @Override
    public synchronized ConnectionSyncResult reconcileRoutes(
            GatewayId gatewayId, RouteRevision revision, Instant updateExpiresAt, List<UserId> users) {
        GatewayRouteTable table = routeTables.computeIfAbsent(gatewayId, GatewayRouteTable::new);
        List<ConnectionRegistration> before = table.registrations();
        ConnectionRouteChangeSet changes = table.reconcile(users, revision, updateExpiresAt);
        indexChanges(changes);
        return new ConnectionSyncResult(
                changes.added().stream().map(id -> table.find(id.userId()).orElseThrow()).toList(),
                before.stream().filter(registration -> changes.removed().contains(registration.id())).toList());
    }

    @Override
    public synchronized int collectExpiredTombstones(Duration gracePeriod) {
        return routeTables.values().stream().mapToInt(table -> table.collectExpiredTombstones(gracePeriod)).sum();
    }

    @Override
    public synchronized ConnectionSyncResult removeGateway(GatewayId gatewayId) {
        GatewayRouteTable removed = routeTables.remove(gatewayId);
        List<ConnectionRegistration> registrations = removed == null ? List.of() : removed.registrations();
        indexChanges(new ConnectionRouteChangeSet(true, List.of(),
                registrations.stream().map(ConnectionRegistration::id).toList()));
        return new ConnectionSyncResult(List.of(), registrations);
    }

    @Override
    public synchronized ConnectionSyncResult purgeRoutes(
            GatewayId gatewayId, RouteRevision revision, Instant oldEventDeadline) {
        GatewayRouteTable table = routeTables.computeIfAbsent(gatewayId, GatewayRouteTable::new);
        List<ConnectionRegistration> before = table.registrations();
        ConnectionRouteChangeSet changes = table.purge(revision, oldEventDeadline);
        indexChanges(changes);
        return new ConnectionSyncResult(List.of(),
                before.stream().filter(registration -> changes.removed().contains(registration.id())).toList());
    }

    private void indexChanges(ConnectionRouteChangeSet changes) {
        for (ConnectionRouteId id : changes.added()) {
            userGateways.computeIfAbsent(id.userId(), ignored -> new LinkedHashSet<>()).add(id.gatewayId());
        }
        for (ConnectionRouteId id : changes.removed()) {
            Set<GatewayId> gateways = userGateways.get(id.userId());
            if (gateways != null) {
                gateways.remove(id.gatewayId());
                if (gateways.isEmpty()) {
                    userGateways.remove(id.userId());
                }
            }
        }
    }
}
