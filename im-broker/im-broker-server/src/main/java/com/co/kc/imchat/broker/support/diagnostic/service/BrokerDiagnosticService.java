package com.co.kc.imchat.broker.support.diagnostic.service;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerInstanceDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerNodeDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerStatisticsDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionMigrationRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionRouteDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GatewayNodeDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GossipRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.support.diagnostic.tracker.impl.ConnectionMigrationTracker;
import com.co.kc.imchat.broker.support.diagnostic.tracker.impl.GossipSyncTracker;
import com.co.kc.imchat.broker.transformer.application.BrokerDiagnosticAppTransformer;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 聚合当前 Broker 节点只读诊断状态。
 */
@Component
@RequiredArgsConstructor
public class BrokerDiagnosticService {
    private static final BrokerDiagnosticAppTransformer TRANSFORMER = BrokerDiagnosticAppTransformer.INSTANCE;
    private static final Instant STARTED_AT = Instant.ofEpochMilli(
            ManagementFactory.getRuntimeMXBean().getStartTime());

    private final BrokerRegistry brokerRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final ConnectionRegistry connectionRegistry;
    private final GossipSyncTracker gossipSyncTracker;
    private final ConnectionMigrationTracker connectionMigrationTracker;
    private final BrokerProperties brokerProperties;

    public BrokerOverviewDTO overview() {
        BrokerInstanceDTO broker = new BrokerInstanceDTO(
                brokerProperties.getInstance().getId(),
                brokerProperties.getInstance().getAddress(),
                STARTED_AT,
                uptimeSeconds());
        BrokerStatisticsDTO statistics = new BrokerStatisticsDTO(
                (long) brokerRegistry.list().size(),
                (long) gatewayRegistry.list().size(),
                connectionRegistry.count());
        return new BrokerOverviewDTO(
                broker,
                statistics,
                gossipSyncTracker.summary(),
                connectionMigrationTracker.summary());
    }

    public List<BrokerNodeDTO> brokers() {
        return brokerRegistry.list().stream().map(TRANSFORMER::brokerFrom).toList();
    }

    public List<GatewayNodeDTO> gateways() {
        return gatewayRegistry.list().stream().map(TRANSFORMER::gatewayFrom).toList();
    }

    public List<ConnectionRouteDTO> connections(Long userId) {
        AssertUtils.argNotNull("userId must not be null", userId);
        AssertUtils.argTrue("userId must be positive", userId > 0);
        return connectionRegistry.find(userId).stream()
                .map(TRANSFORMER::connectionFrom)
                .toList();
    }

    public List<GossipRecordDTO> gossipRecords(Integer limit) {
        return gossipSyncTracker.recent(limit);
    }

    public DiagnosticSummaryDTO gossipSummary() {
        return gossipSyncTracker.summary();
    }

    public List<ConnectionMigrationRecordDTO> migrationRecords(Integer limit) {
        return connectionMigrationTracker.recent(limit);
    }

    private Long uptimeSeconds() {
        return Math.max(0L, Duration.between(STARTED_AT, Instant.now()).toSeconds());
    }
}
