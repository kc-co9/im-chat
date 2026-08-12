package com.co.kc.imchat.broker.domain.service;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.ConnectionMigrationDTO;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.broker.support.event.model.ConnectionRemovedEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Broker 用户连接服务。
 */
@Slf4j
@Component
public class BrokerConnectionService {
    private final ConnectionRegistry connectionRegistry;
    private final BrokerSelector brokerSelector;
    private final BrokerPeerClient brokerPeerClient;
    private final BrokerEventPublisher brokerEventPublisher;

    public BrokerConnectionService(ConnectionRegistry connectionRegistry,
                                   BrokerRegistry brokerRegistry,
                                   BrokerProperties properties,
                                   BrokerPeerClient brokerPeerClient,
                                   BrokerEventPublisher brokerEventPublisher) {
        this.connectionRegistry = connectionRegistry;
        this.brokerSelector = new BrokerSelector(brokerRegistry, properties.getInstance().getId());
        this.brokerPeerClient = brokerPeerClient;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    public Optional<BrokerEndpointDTO> decideBroker(Long userId) {
        return brokerSelector.select(userId);
    }

    public boolean isCurrentBroker(BrokerEndpointDTO broker) {
        return brokerSelector.isCurrentBroker(broker);
    }

    /**
     * 迁移当前 Broker 不再负责的用户连接。
     * <p>
     * Broker 集群成员变化后，重新计算本地连接的目标 Broker，并按目标节点批量迁移。
     * 远端接收成功后删除对应本地连接并发布移除事件；迁移失败时保留本地状态，等待后续重试。
     */
    public void migrateConnection() {
        List<BrokerConnections> brokerConnections = findMigrationConnections();
        for (BrokerConnections brokerConnection : brokerConnections) {
            List<ConnectionMigrationDTO> migrationConnections = brokerConnection.connections()
                    .stream()
                    .map(connection -> new ConnectionMigrationDTO(connection.userId(), connection.gatewayId()))
                    .toList();
            try {
                brokerPeerClient.migrateConnections(brokerConnection.broker(), migrationConnections);
                for (UserGatewayDTO connection : brokerConnection.connections()) {
                    if (connectionRegistry.find(connection.userId()).contains(connection)) {
                        connectionRegistry.unregister(connection.userId(), connection.gatewayId());
                        brokerEventPublisher.publish(new ConnectionRemovedEvent(connection.userId(), connection.gatewayId()));
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("failed to migrate connection snapshot to broker:{}, count:{}, error:{}",
                        brokerConnection.broker().brokerId(), brokerConnection.connections().size(), ex.toString());
            }
        }
    }

    /**
     * 查找当前 Broker 不再负责的用户连接，并按照新的目标 Broker 分组。
     *
     * @return 待迁移的目标 Broker 及其用户连接
     */
    private List<BrokerConnections> findMigrationConnections() {
        Map<BrokerEndpointDTO, List<UserGatewayDTO>> migrationConnections = new LinkedHashMap<>();
        for (UserGatewayDTO connection : connectionRegistry.list()) {
            brokerSelector.select(connection.userId())
                    .filter(broker -> !brokerSelector.isCurrentBroker(broker))
                    .ifPresent(broker ->
                            migrationConnections.computeIfAbsent(broker, key -> new ArrayList<>()).add(connection));
        }
        return migrationConnections.entrySet()
                .stream()
                .map(entry -> new BrokerConnections(entry.getKey(), entry.getValue()))
                .toList();
    }

    private record BrokerConnections(BrokerEndpointDTO broker, List<UserGatewayDTO> connections) {
    }

    private record BrokerSelector(BrokerRegistry brokerRegistry, String currentBrokerId) {

        private Optional<BrokerEndpointDTO> select(Long userId) {
            if (userId == null) {
                return Optional.empty();
            }
            List<BrokerEndpointDTO> brokers = brokerRegistry.list()
                    .stream()
                    .sorted(Comparator.comparing(BrokerEndpointDTO::brokerId))
                    .toList();
            if (brokers.isEmpty()) {
                return Optional.empty();
            }
            int selectedIndex = Math.floorMod(String.valueOf(userId).hashCode(), brokers.size());
            return Optional.of(brokers.get(selectedIndex));
        }

        private boolean isCurrentBroker(BrokerEndpointDTO broker) {
            return broker != null && currentBrokerId.equals(broker.brokerId());
        }
    }
}
