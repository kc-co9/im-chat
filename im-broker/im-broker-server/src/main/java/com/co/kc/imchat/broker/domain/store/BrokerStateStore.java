package com.co.kc.imchat.broker.domain.store;

import com.co.kc.imchat.broker.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
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

/**
 * Broker Gossip 本地状态。
 */
@Component
public class BrokerStateStore implements GossipSyncStore {
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

    public BrokerStateStore(BrokerRegistry brokerRegistry,
                            GatewayRegistry gatewayRegistry,
                            ConnectionRegistry connectionRegistry,
                            BrokerProperties brokerProperties,
                            ClusterProperties properties) {
        this.brokerRegistry = brokerRegistry;
        this.gatewayRegistry = gatewayRegistry;
        this.connectionRegistry = connectionRegistry;
        this.entries = new InMemoryGossipEntryStore(
                brokerProperties.getInstance().getId(), properties::getGossipRemovedTtlMillis);
    }

    public void putBrokerState(String brokerId, String host, int port) {
        BrokerEndpointDTO endpoint = new BrokerEndpointDTO(
                brokerId, host, port, Instant.now(), Instant.now());
        putLocal(GossipKey.broker(brokerId), endpoint);
    }

    public void removeBrokerState(String brokerId) {
        putLocalRemoved(GossipKey.broker(brokerId), null);
    }

    public void putGatewayState(String gatewayId, String host, int port) {
        GatewayEndpointDTO endpoint = new GatewayEndpointDTO(
                gatewayId, host, port, Instant.now(), Instant.now());
        putLocal(GossipKey.gateway(gatewayId), endpoint);
    }

    public void removeGatewayState(String gatewayId) {
        putLocalRemoved(GossipKey.gateway(gatewayId), null);
        removeLocalConnections(gatewayId);
    }

    public void putConnectionState(Long userId,
                                   String gatewayId) {
        ConnectionState payload = new ConnectionState(userId, gatewayId);
        putLocal(GossipKey.connection(userId, gatewayId), payload);
    }

    public void removeConnectionState(Long userId, String gatewayId) {
        ConnectionState payload = new ConnectionState(userId, gatewayId);
        putLocalRemoved(GossipKey.connection(userId, gatewayId), payload);
    }

    public void removeConnectionState(UserGatewayDTO location) {
        removeConnectionState(location.userId(), location.gatewayId());
    }

    public void updateConnectionSnapshot(String gatewayId,
                                         List<Long> userIds,
                                         List<UserGatewayDTO> removedLocations) {
        if (removedLocations != null) {
            removedLocations.forEach(this::removeConnectionState);
        }
        List<Long> activeUserIds = userIds == null ? List.of() : userIds;
        entries.entries().stream()
                .filter(GossipEntityType.CONNECTION::matches)
                .filter(entry -> entry.operation() != GossipDeltaOperation.REMOVED)
                .map(entry -> new ConnectionGossipEntry(entry, GossipKey.from(entry.key()).connectionKey()))
                .filter(entry -> gatewayId.equals(entry.connectionKey().gatewayId()))
                .filter(entry -> !activeUserIds.contains(entry.connectionKey().userId()))
                .forEach(entry -> putLocalRemoved(entry.delta().key(),
                        GossipEntityType.CONNECTION,
                        readConnectionState(entry.delta().payload(), entry.connectionKey()).orElse(null)));
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

    private void syncRegistry(GossipDeltaEntry delta) {
        if (delta.operation() == GossipDeltaOperation.REMOVED) {
            removeRegistryEntry(delta);
            return;
        }
        switch (delta.entityType()) {
            case BROKER -> {
                BrokerEndpointDTO broker = JsonUtils.fromJson(delta.payload(), BrokerEndpointDTO.class);
                brokerRegistry.register(broker.brokerId(), broker.host(), broker.port());
            }
            case GATEWAY -> {
                GatewayEndpointDTO gateway = JsonUtils.fromJson(delta.payload(), GatewayEndpointDTO.class);
                gatewayRegistry.register(gateway.gatewayId(), gateway.host(), gateway.port());
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
                gatewayRegistry.unregister(key.id());
                removeLocalConnections(key.id());
            }
            case CONNECTION -> {
                Optional<ConnectionState> state = readConnectionState(
                        delta.payload(), key.connectionKey());
                state.ifPresent(connection ->
                        connectionRegistry.unregister(connection.userId(), connection.gatewayId()));
            }
        }
    }

    private void removeLocalConnections(String gatewayId) {
        connectionRegistry.sync(gatewayId, List.of()).forEach(this::removeConnectionState);
    }

    private Optional<ConnectionState> readConnectionState(String payload, ConnectionKey connectionKey) {
        if (payload == null || payload.isBlank() || "null".equals(payload)) {
            return Optional.of(connectionKey.toState());
        }
        try {
            ConnectionState state = JsonUtils.fromJson(payload, ConnectionState.class);
            if (state.userId() != null && state.gatewayId() != null) {
                return Optional.of(state);
            }
        } catch (RuntimeException ignored) {
        }
        return Optional.of(connectionKey.toState());
    }

    private record ConnectionGossipEntry(GossipDeltaEntry delta, ConnectionKey connectionKey) {
    }

    /**
     * 用户连接 gossip 状态载荷。
     */
    private record ConnectionState(Long userId, String gatewayId) {
    }

    private record GossipKey(GossipEntityType entityType, String id) {
        private static GossipKey broker(String brokerId) {
            return new GossipKey(GossipEntityType.BROKER, brokerId);
        }

        private static GossipKey gateway(String gatewayId) {
            return new GossipKey(GossipEntityType.GATEWAY, gatewayId);
        }

        private static GossipKey connection(Long userId, String gatewayId) {
            return new GossipKey(GossipEntityType.CONNECTION, ConnectionKey.of(userId, gatewayId).value());
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

        private ConnectionKey connectionKey() {
            return ConnectionKey.from(id);
        }
    }

    private record ConnectionKey(Long userId, String gatewayId) {
        private static ConnectionKey of(Long userId, String gatewayId) {
            return new ConnectionKey(userId, gatewayId);
        }

        private static ConnectionKey from(String value) {
            int separatorIndex = value.indexOf(KEY_SEPARATOR);
            return new ConnectionKey(
                    Long.valueOf(value.substring(0, separatorIndex)),
                    value.substring(separatorIndex + 1));
        }

        private String value() {
            return userId + KEY_SEPARATOR + gatewayId;
        }

        private ConnectionState toState() {
            return new ConnectionState(userId, gatewayId);
        }
    }
}
