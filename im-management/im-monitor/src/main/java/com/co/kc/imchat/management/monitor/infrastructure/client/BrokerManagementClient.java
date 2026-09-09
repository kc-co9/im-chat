package com.co.kc.imchat.management.monitor.infrastructure.client;

import com.co.kc.imchat.management.monitor.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.BrokerNodePayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.BrokerOverviewPayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.ConnectionRoutePayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.GatewayNodePayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.GossipRecordPayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.MigrationRecordPayload;

import java.util.List;

/** Broker 节点管理 HTTP 客户端能力。 */
public interface BrokerManagementClient {
    BrokerOverviewPayload overview(BrokerManagementEndpoint endpoint);

    List<BrokerNodePayload> brokers(BrokerManagementEndpoint endpoint);

    List<GatewayNodePayload> gateways(BrokerManagementEndpoint endpoint);

    List<ConnectionRoutePayload> connections(BrokerManagementEndpoint endpoint, Long userId);

    List<GossipRecordPayload> gossipRecords(BrokerManagementEndpoint endpoint, int limit);

    List<MigrationRecordPayload> migrationRecords(BrokerManagementEndpoint endpoint, int limit);
}
