package com.co.kc.imchat.broker.application.diagnostic;

import com.co.kc.imchat.broker.domain.connection.model.UserId;

import com.co.kc.imchat.broker.adapter.diagnostic.GossipSyncTracker;

import com.co.kc.imchat.broker.model.cqrs.query.ConnectionRoutesQuery;
import com.co.kc.imchat.broker.model.cqrs.query.GossipRecordsQuery;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerInstanceDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerStatisticsDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.transformer.application.BrokerDiagnosticAppTransformer;
import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.RequiredArgsConstructor;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 聚合当前 Broker 节点只读诊断状态。
 */
@RequiredArgsConstructor
public class BrokerDiagnosticAppService {
    private static final BrokerDiagnosticAppTransformer TRANSFORMER = BrokerDiagnosticAppTransformer.INSTANCE;
    private static final Instant STARTED_AT = Instant.ofEpochMilli(
            ManagementFactory.getRuntimeMXBean().getStartTime());

    private final BrokerRegistry brokerRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final ConnectionRegistry connectionRegistry;
    private final GossipSyncTracker gossipSyncTracker;
    private final String brokerId;
    private final String address;

    public BrokerOverviewDTO overview() {
        BrokerInstanceDTO broker = new BrokerInstanceDTO(
                brokerId,
                address,
                STARTED_AT,
                uptimeSeconds());
        BrokerStatisticsDTO statistics = new BrokerStatisticsDTO(
                (long) brokerRegistry.list().size(),
                (long) gatewayRegistry.list().size(),
                connectionRegistry.count());
        return new BrokerOverviewDTO(
                broker,
                statistics,
                gossipSyncTracker.summary());
    }

    public List<BrokerNodeDTO> brokers() {
        return brokerRegistry.list().stream().map(TRANSFORMER::brokerFrom).toList();
    }

    public List<GatewayNodeDTO> gateways() {
        return gatewayRegistry.list().stream().map(TRANSFORMER::gatewayFrom).toList();
    }

    public List<ConnectionRouteDTO> connections(ConnectionRoutesQuery query) {
        Long userId = query.userId();
        AssertUtils.argNotNull("userId must not be null", userId);
        AssertUtils.argTrue("userId must be positive", userId > 0);
        return connectionRegistry.find(new UserId(userId)).stream()
                .map(TRANSFORMER::connectionFrom)
                .toList();
    }

    public List<GossipRecordDTO> gossipRecords(GossipRecordsQuery query) {
        return gossipSyncTracker.recent(query.limit());
    }

    public DiagnosticSummaryDTO gossipSummary() {
        return gossipSyncTracker.summary();
    }

    private Long uptimeSeconds() {
        return Math.max(0L, Duration.between(STARTED_AT, Instant.now()).toSeconds());
    }
}
