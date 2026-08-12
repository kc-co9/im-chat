package com.co.kc.imchat.broker.domain.registry.connection.memory;

import com.co.kc.imchat.common.utils.NestedMapUtils;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConnectionRegistry implements ConnectionRegistry {

    /* <UserId, <GatewayId, UserGatewayDTO>> */
    private final Map<Long, Map<String, UserGatewayDTO>> userConnections = new ConcurrentHashMap<>();

    /* <GatewayId, <UserId, UserGatewayDTO>> */
    private final Map<String, Map<Long, UserGatewayDTO>> gatewayConnections = new ConcurrentHashMap<>();

    @Override
    public void register(Long userId, String gatewayId) {
        UserGatewayDTO connection = new UserGatewayDTO(userId, gatewayId, Instant.now(), Instant.now());
        userConnections.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(gatewayId, connection);
        gatewayConnections.computeIfAbsent(gatewayId, ignored -> new ConcurrentHashMap<>())
                .put(userId, connection);
    }

    @Override
    public void unregister(Long userId, String gatewayId) {
        NestedMapUtils.prune(userConnections, userId, gatewayId);
        NestedMapUtils.prune(gatewayConnections, gatewayId, userId);
    }

    @Override
    public List<UserGatewayDTO> find(Long userId) {
        Map<String, UserGatewayDTO> connections = userConnections.get(userId);
        if (connections == null) {
            return List.of();
        }
        return new ArrayList<>(connections.values());
    }

    @Override
    public List<UserGatewayDTO> list() {
        return userConnections.values().stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public List<UserGatewayDTO> sync(String gatewayId, List<Long> userIds) {
        Set<Long> aliveUserIds = userIds == null ? Set.of() : new HashSet<>(userIds);
        Map<Long, UserGatewayDTO> connections = gatewayConnections.get(gatewayId);
        if (connections == null) {
            return List.of();
        }
        List<UserGatewayDTO> removedConnections = new ArrayList<>();
        for (Long userId : connections.keySet()) {
            UserGatewayDTO connection = connections.get(userId);
            if (aliveUserIds.contains(userId)) {
                continue;
            }
            if (connections.remove(userId, connection)) {
                NestedMapUtils.prune(userConnections, userId, gatewayId);
                removedConnections.add(connection);
            }
        }
        if (connections.isEmpty()) {
            gatewayConnections.remove(gatewayId, connections);
        }
        return removedConnections;
    }
}
