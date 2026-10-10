package com.co.kc.imchat.management.ops.infrastructure.client;

import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOverviewPayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterStatePayload;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.infrastructure.client.model.ConnectionRoutePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.GatewayNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.GossipRecordPayload;

import java.util.List;

/** Broker 节点管理 HTTP 客户端能力。 */
public interface BrokerManagementClient {
    BrokerOverviewPayload overview(BrokerManagementEndpoint endpoint);

    List<BrokerNodePayload> brokers(BrokerManagementEndpoint endpoint);

    List<GatewayNodePayload> gateways(BrokerManagementEndpoint endpoint);

    List<ConnectionRoutePayload> connections(BrokerManagementEndpoint endpoint, Long userId);

    List<GossipRecordPayload> gossipRecords(BrokerManagementEndpoint endpoint, int limit);

    BrokerOperationPayload operate(BrokerManagementEndpoint endpoint,
                                   BrokerClusterOperationCommand command,
                                   String scope);

    BrokerClusterStatePayload clusterState(BrokerManagementEndpoint endpoint);
}
