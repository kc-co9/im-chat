package com.co.kc.imchat.management.ops.adapter;

import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.discovery.BrokerDiscovery;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.management.ops.transformer.infrastructure.BrokerClientTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 适配 Broker 发现与管理 HTTP 协议。 */
@Component
@RequiredArgsConstructor
public class BrokerDiagnosticAdapter {
    private final BrokerDiscovery brokerDiscovery;
    private final BrokerManagementClient managementClient;

    public List<BrokerManagementEndpoint> discover() {
        return brokerDiscovery.discover();
    }

    public BrokerOverviewDTO overview(BrokerManagementEndpoint endpoint) {
        return BrokerClientTransformer.INSTANCE.brokerOverviewDataFrom(
                managementClient.overview(endpoint));
    }

    public List<BrokerNodeDTO> brokers(BrokerManagementEndpoint endpoint) {
        return BrokerClientTransformer.INSTANCE.brokerNodeDataFrom(
                managementClient.brokers(endpoint));
    }

    public List<GatewayNodeDTO> gateways(BrokerManagementEndpoint endpoint) {
        return BrokerClientTransformer.INSTANCE.gatewayNodeDataFrom(
                managementClient.gateways(endpoint));
    }

    public List<ConnectionRouteDTO> connections(BrokerManagementEndpoint endpoint, Long userId) {
        return BrokerClientTransformer.INSTANCE.connectionRouteDataFrom(
                managementClient.connections(endpoint, userId));
    }

    public List<GossipRecordDTO> gossipRecords(BrokerManagementEndpoint endpoint, Integer limit) {
        return BrokerClientTransformer.INSTANCE.gossipRecordDataFrom(
                managementClient.gossipRecords(endpoint, limit));
    }

}
