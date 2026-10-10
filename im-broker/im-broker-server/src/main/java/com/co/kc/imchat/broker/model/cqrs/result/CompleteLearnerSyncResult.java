package com.co.kc.imchat.broker.model.cqrs.result;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;

/** 标记 learner 已就绪的应用服务结果。 */
public record CompleteLearnerSyncResult(
        /* 同步完成请求的操作标识，用于关联幂等重试。 */
        String operationId,
        /* 本次处理状态，不以字符串传递共识状态。 */
        BrokerClusterConsensusStatus status,
        /* 操作接受时的路由 epoch；尚未登记操作时为本次读取的 epoch。 */
        long acceptedEpoch,
        /* 已关联操作的目标 epoch；尚未关联操作时为空。 */
        Long targetEpoch,
        /* 当前已知的 Leader 地址；未知时为空。 */
        String leaderEndpoint,
        /* 冲突、拒绝或不可用原因；没有附加说明时为空。 */
        String message) {

    /** 新请求与当前集群 epoch 冲突，不登记同步完成操作。 */
    public static CompleteLearnerSyncResult conflict(ClusterOperationId operationId, RoutingEpoch epoch) {
        return new CompleteLearnerSyncResult(operationId.value(), BrokerClusterConsensusStatus.CONFLICT,
                epoch.value(), null, null, "expected epoch does not match committed state");
    }

    /** 新请求端点与已提交绑定不一致，不登记同步完成操作。 */
    public static CompleteLearnerSyncResult rejected(ClusterOperationId operationId, RoutingEpoch epoch) {
        return new CompleteLearnerSyncResult(operationId.value(), BrokerClusterConsensusStatus.REJECTED,
                epoch.value(), null, null, "broker membership endpoint does not match committed binding");
    }

    /** 直接返回未接受的领域判断，已关联操作仍使用原接受 epoch 与目标 epoch。 */
    public static CompleteLearnerSyncResult failure(
            ClusterOperationId operationId, RoutingEpoch epoch, BrokerClusterDecision decision) {
        BrokerClusterOperation operation = decision.operation();
        return new CompleteLearnerSyncResult(operationId.value(),
                BrokerClusterConsensusStatus.valueOf(decision.status().name()),
                operation == null ? epoch.value() : operation.acceptedEpoch().value(),
                operation == null ? null : operation.resultEpoch().value(), null, decision.reason().value());
    }

}
