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
import com.co.kc.imchat.management.ops.model.cqrs.dto.SourcedValueDTO;
import org.springframework.stereotype.Component;

import com.co.kc.imchat.management.ops.transformer.infrastructure.BrokerClientTransformer;

import java.util.List;

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
                .filter(BrokerManagementEndpoint::isSupported)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Broker not found"));
        return from(client.operate(endpoint, command, command.scope()));
    }

    /** 按 Broker 返回的可信 Leader hint 重试同一 operationId。 */
    public BrokerOperationDTO operateLeader(String leaderHint,
                                            BrokerClusterOperationCommand command) {
        BrokerManagementEndpoint endpoint = discovery.resolveLeader(leaderHint);
        return from(client.operate(endpoint, command, command.scope()));
    }

    /** 在发现到的 Broker 上查询既有 operation；网络失败保持 UNKNOWN 语义。 */
    public BrokerOperationDTO.Query queryOperation(String operationId) {
        List<BrokerManagementEndpoint> endpoints;
        try {
            endpoints = discovery.discover();
        } catch (RuntimeException exception) {
            return unknownOperation(operationId);
        }
        for (BrokerManagementEndpoint endpoint : endpoints) {
            if (!endpoint.isSupported()) {
                continue;
            }
            try {
                BrokerOperationPayload.Query payload = client.queryOperation(endpoint, operationId);
                if (payload != null && operationId.equals(payload.operationId())) {
                    return BrokerClientTransformer.INSTANCE.operationQueryFrom(payload);
                }
            } catch (RuntimeException exception) {
                // 节点查询失败不证明原操作未提交；仍可查询其余发现节点。
            }
        }
        return unknownOperation(operationId);
    }

    private BrokerOperationDTO.Query unknownOperation(String operationId) {
        return new BrokerOperationDTO.Query(operationId, null, null, "UNKNOWN",
                null, null, "Operation query unavailable; submission outcome is unknown", null, null);
    }

    public BrokerClusterStateDTO clusterState() {
        return sourcedClusterState().value();
    }

    /** 返回带来源 Broker 标识的集群状态投影，避免把观测结果误称为线性一致读取。 */
    public SourcedValueDTO<BrokerClusterStateDTO> sourcedClusterState() {
        RuntimeException lastFailure = null;
        for (BrokerManagementEndpoint endpoint : discovery.discover()) {
            if (!endpoint.isSupported()) {
                continue;
            }
            try {
                return new SourcedValueDTO<>(endpoint.brokerId(), from(client.clusterState(endpoint)));
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
