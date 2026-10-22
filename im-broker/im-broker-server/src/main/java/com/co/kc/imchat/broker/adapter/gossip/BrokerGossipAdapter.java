package com.co.kc.imchat.broker.adapter.gossip;

import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;

import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;
import com.co.kc.imchat.plugin.gossip.model.GossipEntityType;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaOperation;
import com.co.kc.imchat.plugin.gossip.model.GossipDigestEntry;
import com.co.kc.imchat.plugin.gossip.store.InMemoryGossipEntryStore;
import com.co.kc.imchat.plugin.gossip.sync.GossipSyncStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/**
 * 将 Broker 运行观察接入通用 Gossip 同步机制。
 *
 * <p>本机注册、心跳及连接变化转换为带版本的 Gossip 条目；digest/delta、版本合并和删除墓碑
 * 由 {@link InMemoryGossipEntryStore} 管理。本类不发起 peer RPC，也不改变 Raft 权威拓扑或路由授权。</p>
 *
 * <p>合并后的 Broker/Gateway 观察投影到本机 Registry；远端 Connection 观察只保存在 Gossip 表，
 * 不能绕过 RouteBroker 归属与 routeRevision 校验写入本机连接路由。</p>
 */
@Component
public class BrokerGossipAdapter implements GossipSyncStore {
    private static final String KEY_SEPARATOR = ":";

    private final BrokerRegistry brokerRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final ConnectionRegistry connectionRegistry;
    /**
     * 本机已知的 gossip 状态表。
     * <p>
     * key 为 gossip 实体唯一标识，value 为带版本号的同步数据。Broker/Gateway 状态会投影到本地 registry；
     * Connection 状态只作为集群同步视图保存，业务连接 registry 只保存当前 broker 归属的本地连接。
     */
    private final InMemoryGossipEntryStore entries;

    public BrokerGossipAdapter(BrokerRegistry brokerRegistry,
                            GatewayRegistry gatewayRegistry,
                            ConnectionRegistry connectionRegistry,
                            BrokerProperties brokerProperties,
                            ClusterProperties properties) {
        this.brokerRegistry = brokerRegistry;
        this.gatewayRegistry = gatewayRegistry;
        this.connectionRegistry = connectionRegistry;
        this.entries = new InMemoryGossipEntryStore(
                brokerProperties.getInstance().getId(), properties::getGossipRemovedTtlMs);
    }

    /** 记录本机获知的 Broker 注册或心跳观察，由 Gossip 存储分配本次更新版本。 */
    public void putBrokerState(String brokerId, String host, int port) {
        BrokerRegistration endpoint = new BrokerRegistration(
                brokerId, host, port, Instant.now(), Instant.now());
        putLocal(GossipKey.broker(brokerId), endpoint);
    }

    /** 写入 Broker 删除墓碑以传播移除观察，不直接修改 Raft 成员或流量状态。 */
    public void removeBrokerState(String brokerId) {
        putLocalRemoved(GossipKey.broker(brokerId), null);
    }

    /** 保存 Gateway 端点与认证主体的传输投影，供其他 Broker 合并注册观察。 */
    public void putGatewayState(String gatewayId, String host, int port,
                                String workloadIdentity) {
        GatewayEndpointDTO endpoint = new GatewayEndpointDTO(
                gatewayId, host, port, workloadIdentity, Instant.now(), Instant.now());
        putLocal(GossipKey.gateway(gatewayId), endpoint);
    }

    /** 记录 Gateway 及其已知连接的删除观察；本机业务 Registry 的清理由调用方编排。 */
    public void removeGatewayState(String gatewayId) {
        putLocalRemoved(GossipKey.gateway(gatewayId), null);
        updateConnectionSnapshot(gatewayId, List.of(), List.of());
    }

    /** 记录用户在指定 Gateway 的连接位置观察，不复制连接版本或本机执行许可。 */
    public void putConnectionState(Long userId,
                                   String gatewayId) {
        ConnectionState payload = new ConnectionState(userId, gatewayId);
        putLocal(GossipKey.connection(userId, gatewayId), payload);
    }

    /** 为单个用户/Gateway 位置写入删除墓碑，不直接删除业务连接路由。 */
    public void removeConnectionState(Long userId, String gatewayId) {
        ConnectionState payload = new ConnectionState(userId, gatewayId);
        putLocalRemoved(GossipKey.connection(userId, gatewayId), payload);
    }

    public void removeConnectionState(ConnectionRegistration location) {
        removeConnectionState(location.userId().value(), location.gatewayId().value());
    }

    /**
     * 按 Gateway 的当前完整用户集合，将 Gossip 表中已不在集合内的位置标为删除。
     *
     * <p>显式移除的位置先写墓碑；随后只扫描该 Gateway 的活跃连接观察。用户集合为空或 null
     * 表示没有活跃用户。本方法只清理观察，不为集合中的用户补写注册，也不替换业务路由表。</p>
     */
    public void updateConnectionSnapshot(String gatewayId,
                                         List<Long> userIds,
                                         List<ConnectionRegistration> removedLocations) {
        if (removedLocations != null) {
            removedLocations.forEach(this::removeConnectionState);
        }
        Set<Long> activeUserIds = userIds == null ? Set.of() : new HashSet<>(userIds);
        for (GossipDeltaEntry entry : entries.entries()) {
            if (!GossipEntityType.CONNECTION.matches(entry)
                    || entry.operation() == GossipDeltaOperation.REMOVED) {
                continue;
            }
            ConnectionRouteId key = GossipKey.from(entry.key()).connectionKey();
            if (gatewayId.equals(key.gatewayId().value()) && !activeUserIds.contains(key.userId().value())) {
                putLocalRemoved(entry.key(), GossipEntityType.CONNECTION,
                        readConnectionState(entry.payload(), key).orElse(null));
            }
        }
    }

    @Override
    public List<GossipDigestEntry> digest() {
        return entries.digest();
    }

    @Override
    public List<String> keysNewerThan(List<GossipDigestEntry> remoteDigest) {
        return entries.keysNewerThan(remoteDigest);
    }

    @Override
    public List<String> keysOlderThan(List<GossipDigestEntry> remoteDigest) {
        return entries.keysOlderThan(remoteDigest);
    }

    @Override
    public List<GossipDeltaEntry> deltas(List<String> keys) {
        return entries.deltas(keys);
    }

    /** 合并远端版本后，只将存储返回的已接受变更投影到 Registry，避免旧观察回退本机状态。 */
    @Override
    public void merge(List<GossipDeltaEntry> deltas) {
        entries.merge(deltas).forEach(this::syncRegistry);
    }

    private void putLocal(GossipKey key, Object payload) {
        entries.put(key.value(), key.entityType(), JsonUtils.toJson(payload));
    }

    private void putLocalRemoved(GossipKey key, Object payload) {
        putLocalRemoved(key.value(), key.entityType(), payload);
    }

    private void putLocalRemoved(String key, GossipEntityType entityType, Object payload) {
        String value = payload instanceof String text ? text : JsonUtils.toJson(payload);
        entries.putRemoved(key, entityType, value);
    }

    /** Broker/Gateway 更新可回写注册观察；Connection 更新不能取得本机路由所有权。 */
    private void syncRegistry(GossipDeltaEntry delta) {
        if (delta.operation() == GossipDeltaOperation.REMOVED) {
            removeRegistryEntry(delta);
            return;
        }
        switch (delta.entityType()) {
            case BROKER -> {
                BrokerRegistration broker = JsonUtils.fromJson(delta.payload(), BrokerRegistration.class);
                brokerRegistry.register(broker.brokerId(), broker.host(), broker.port());
            }
            case GATEWAY -> {
                GatewayEndpointDTO gateway = JsonUtils.fromJson(delta.payload(), GatewayEndpointDTO.class);
                gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom(
                        gateway.gatewayId(), gateway.host(), gateway.port(), gateway.workloadIdentity()));
            }
            case CONNECTION -> {
                // ConnectionRegistry 只保存当前 broker 归属的连接；远端连接状态只保留在 gossip entries 中。
            }
        }
    }

    private void removeRegistryEntry(GossipDeltaEntry delta) {
        GossipKey key = GossipKey.from(delta.key());
        switch (delta.entityType()) {
            case BROKER -> brokerRegistry.unregister(key.id());
            case GATEWAY -> {
                gatewayRegistry.unregister(new GatewayId(key.id()));
                removeLocalConnections(key.id());
            }
            case CONNECTION -> {
                // Gossip 删除只收敛远端观察，不携带 Gateway revision，不能改写本机权威路由。
            }
        }
    }

    /** Gateway 整体移除时清理其本机连接，并将实际移除的位置继续写为 Gossip 删除观察。 */
    private void removeLocalConnections(String gatewayId) {
        connectionRegistry.removeGateway(new GatewayId(gatewayId)).removed().forEach(this::removeConnectionState);
    }

    /** 载荷缺失或无法解析时从条目 key 恢复位置身份，保证仍能为该位置生成删除观察。 */
    private Optional<ConnectionState> readConnectionState(String payload, ConnectionRouteId connectionKey) {
        if (payload == null || payload.isBlank() || "null".equals(payload)) {
            return Optional.of(new ConnectionState(connectionKey.userId().value(), connectionKey.gatewayId().value()));
        }
        try {
            ConnectionState state = JsonUtils.fromJson(payload, ConnectionState.class);
            if (state.userId() != null && state.gatewayId() != null) {
                return Optional.of(state);
            }
        } catch (RuntimeException ignored) {
        }
        return Optional.of(new ConnectionState(connectionKey.userId().value(), connectionKey.gatewayId().value()));
    }

    /**
     * 用户连接 gossip 状态载荷。
     */
    private record ConnectionState(Long userId, String gatewayId) {
    }

    /** Gossip 条目身份，编码为实体类型前缀与该类型内的业务标识。 */
    private record GossipKey(GossipEntityType entityType, String id) {
        private static GossipKey broker(String brokerId) {
            return new GossipKey(GossipEntityType.BROKER, brokerId);
        }

        private static GossipKey gateway(String gatewayId) {
            return new GossipKey(GossipEntityType.GATEWAY, gatewayId);
        }

        private static GossipKey connection(Long userId, String gatewayId) {
            ConnectionRouteId routeId = new ConnectionRouteId(new UserId(userId), new GatewayId(gatewayId));
            return new GossipKey(GossipEntityType.CONNECTION,
                    routeId.userId().value() + KEY_SEPARATOR + routeId.gatewayId().value());
        }

        private static GossipKey from(String value) {
            int separatorIndex = value.indexOf(KEY_SEPARATOR);
            return new GossipKey(
                    GossipEntityType.valueOf(value.substring(0, separatorIndex)),
                    value.substring(separatorIndex + 1));
        }

        private String value() {
            return entityType.name() + KEY_SEPARATOR + id;
        }

        private ConnectionRouteId connectionKey() {
            int separatorIndex = id.indexOf(KEY_SEPARATOR);
            return new ConnectionRouteId(
                    new UserId(Long.valueOf(id.substring(0, separatorIndex))),
                    new GatewayId(id.substring(separatorIndex + 1)));
        }
    }
}
