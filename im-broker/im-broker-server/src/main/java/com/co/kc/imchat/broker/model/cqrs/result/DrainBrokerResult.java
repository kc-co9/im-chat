package com.co.kc.imchat.broker.model.cqrs.result;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;

/** 摘流 Broker 的应用服务结果。 */
public record DrainBrokerResult(
        /* 请求的幂等操作标识。 */
        String operationId,
        /* 本次共识处理状态；不表示路由重建已经完成。 */
        String status,
        /* 操作接受时的 epoch；未登记时为当前观察值。 */
        long acceptedEpoch,
        /* 已关联操作的目标 epoch；尚未关联时为空。 */
        Long targetEpoch,
        /* 当前已知的 Leader 成员端点；未知时为空。 */
        String leaderEndpoint,
        /* 冲突、拒绝、等待或故障的具体说明。 */
        String message) {

    /** 新请求基于过期 epoch，不提交操作或执行外部变更。 */
    public static DrainBrokerResult conflict(ClusterOperationId operationId, RoutingEpoch epoch) {
        return new DrainBrokerResult(operationId.value(), "CONFLICT", epoch.value(), null, null,
                "expected epoch does not match committed state");
    }

    /** 返回领域拒绝，已关联的历史操作保留原接受 epoch 和目标 epoch。 */
    public static DrainBrokerResult failure(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterDecision decision) {
        BrokerClusterOperation operation = decision.operation();
        return new DrainBrokerResult(operationId.value(), decision.status().name(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), null, decision.reason().value());
    }
}
