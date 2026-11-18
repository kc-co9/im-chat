package com.co.kc.imchat.management.ops.application;

import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.annotation.AuditAttribute;
import com.co.kc.imchat.management.ops.model.cqrs.query.BrokerOperationQuery;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.ops.adapter.BrokerClusterOperationAdapter;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterStateDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.SourcedValueDTO;
import org.springframework.stereotype.Component;

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
    public BrokerOperationDTO operate(@AuditAttribute BrokerClusterOperationCommand command) {
        return submitOperation(command);
    }

    private BrokerOperationDTO submitOperation(BrokerClusterOperationCommand command) {
        BrokerOperationDTO first = adapter.operate(command);
        if (!"NOT_LEADER".equals(first.status())
                || first.leaderEndpoint() == null
                || first.leaderEndpoint().isBlank()) {
            return first;
        }
        return adapter.operateLeader(first.leaderEndpoint(), command);
    }

    /** 查询既有 operation，未知结果由适配器保持为 UNKNOWN。 */
    public BrokerOperationDTO.Query queryOperation(BrokerOperationQuery query) {
        return adapter.queryOperation(query.operationId());
    }

    /** 返回带来源的集群状态观测值。 */
    public SourcedValueDTO<BrokerClusterStateDTO> sourcedClusterState() {
        return adapter.sourcedClusterState();
    }

    public BrokerClusterStateDTO clusterState() {
        return adapter.clusterState();
    }
}
