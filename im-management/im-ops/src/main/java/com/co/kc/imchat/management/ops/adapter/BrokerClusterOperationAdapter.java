package com.co.kc.imchat.management.ops.adapter;

import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterStatePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import com.co.kc.imchat.management.ops.infrastructure.discovery.BrokerDiscovery;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterStateDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import org.springframework.stereotype.Component;

import java.net.URI;

/** 隔离 Broker 发现、管理 HTTP 载荷与 Ops 应用模型。 */
@Component
public class BrokerClusterOperationAdapter {
    private final BrokerDiscovery discovery;
    private final BrokerManagementClient client;

    public BrokerClusterOperationAdapter(BrokerDiscovery discovery,
                                         BrokerManagementClient client) {
        this.discovery = discovery;
        this.client = client;
    }

    public BrokerOperationDTO operate(BrokerClusterOperationCommand command) {
        BrokerManagementEndpoint endpoint = discovery.discover().stream()
                .filter(candidate -> command.brokerId().equals(candidate.brokerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Broker not found"));
        return from(client.operate(endpoint, command, command.scope()));
    }

    public BrokerOperationDTO operateLeader(URI leaderUri,
                                            BrokerClusterOperationCommand command) {
        return from(client.operate(
                new BrokerManagementEndpoint("raft-leader", leaderUri, null), command,
                command.scope()));
    }

    public BrokerClusterStateDTO clusterState() {
        RuntimeException lastFailure = null;
        for (BrokerManagementEndpoint endpoint : discovery.discover()) {
            if (!endpoint.isSupported()) {
                continue;
            }
            try {
                return from(client.clusterState(endpoint));
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("No Broker management endpoint is available")
                : lastFailure;
    }

    private BrokerOperationDTO from(BrokerOperationPayload payload) {
        return new BrokerOperationDTO(payload.operationId(), payload.status(),
                payload.acceptedEpoch(), payload.targetEpoch(), payload.leaderEndpoint(),
                payload.message());
    }

    private BrokerClusterStateDTO from(BrokerClusterStatePayload payload) {
        return new BrokerClusterStateDTO(payload.revision(), payload.epoch(),
                payload.algorithm(), payload.brokers().stream().map(node ->
                        new BrokerClusterNodeDTO(node.brokerId(), node.routeEndpoint(),
                                node.membershipRole(), node.routingState())).toList());
    }
}
