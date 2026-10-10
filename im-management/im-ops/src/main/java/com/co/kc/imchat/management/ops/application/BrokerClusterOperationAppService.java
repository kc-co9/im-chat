package com.co.kc.imchat.management.ops.application;

import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.ops.adapter.BrokerClusterOperationAdapter;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterStateDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import org.springframework.stereotype.Component;

import java.net.URI;

/** 发起 Broker operation，并使用同一 operationId 最多跟随一次 Leader hint。 */
@Component
public class BrokerClusterOperationAppService {
    private final BrokerClusterOperationAdapter adapter;

    public BrokerClusterOperationAppService(BrokerClusterOperationAdapter adapter) {
        this.adapter = adapter;
    }

    @Audited(type = AuditType.BUSINESS, action = "BROKER_CLUSTER_OPERATION",
            targetType = "BROKER", targetId = "#command.brokerId()",
            description = "提交 Broker 集群操作")
    public BrokerOperationDTO operate(BrokerClusterOperationCommand command) {
        BrokerOperationDTO first = adapter.operate(command);
        if (!"NOT_LEADER".equals(first.status()) || first.leaderEndpoint() == null
                || first.leaderEndpoint().isBlank()) {
            return first;
        }
        URI leaderUri = URI.create(first.leaderEndpoint());
        if (!"http".equals(leaderUri.getScheme()) && !"https".equals(leaderUri.getScheme())) {
            throw new IllegalArgumentException("Broker returned an invalid Leader hint");
        }
        return adapter.operateLeader(leaderUri, command);
    }

    public BrokerClusterStateDTO clusterState() {
        return adapter.clusterState();
    }
}
