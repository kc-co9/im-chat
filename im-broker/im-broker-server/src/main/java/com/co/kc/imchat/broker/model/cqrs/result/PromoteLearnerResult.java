package com.co.kc.imchat.broker.model.cqrs.result;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;

/** 将 learner 提升为 voter 的应用服务结果。 */
public record PromoteLearnerResult(
        String operationId,
        String status,
        long acceptedEpoch,
        Long targetEpoch,
        String leaderEndpoint,
        String message) {
    /** 直接返回未接受的领域判断，保留已关联操作的接受 epoch 和目标 epoch。 */
    public static PromoteLearnerResult failure(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterDecision decision) {
        BrokerClusterOperation operation = decision.operation();
        return new PromoteLearnerResult(operationId.value(), decision.status().name(),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), null, decision.reason().value());
    }

    public static PromoteLearnerResult conflict(ClusterOperationId operationId, RoutingEpoch epoch) {
        return new PromoteLearnerResult(operationId.value(), "CONFLICT", epoch.value(), null, null,
                "expected epoch does not match committed state");
    }

    public static PromoteLearnerResult rejected(ClusterOperationId operationId, RoutingEpoch epoch) {
        return new PromoteLearnerResult(operationId.value(), "REJECTED", epoch.value(), null, null,
                "broker membership endpoint does not match committed binding");
    }
}
