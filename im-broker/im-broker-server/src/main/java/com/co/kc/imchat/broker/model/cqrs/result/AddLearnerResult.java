package com.co.kc.imchat.broker.model.cqrs.result;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;

/** 将 Broker 加入为 learner 的应用服务结果。 */
public record AddLearnerResult(
        /* 调用方提供的操作标识，用于关联本次加入请求及其幂等重试。 */
        String operationId,
        /* 本次处理状态：APPLIED、DUPLICATE、CONFLICT、REJECTED、NOT_LEADER 或 UNAVAILABLE；不表示 learner 已完成日志同步。 */
        BrokerClusterConsensusStatus status,
        /* 当前已知的 Leader 成员通信地址；未知时为空，不一定是 HTTP 请求地址。 */
        String leaderEndpoint,
        /* 冲突、拒绝或暂不可用的原因等处理说明；没有附加说明时为空。 */
        String message) {

    /** 请求基于过期集群状态，不执行 learner 成员变更。 */
    public static AddLearnerResult conflict(ClusterOperationId operationId) {
        return new AddLearnerResult(operationId.value(), BrokerClusterConsensusStatus.CONFLICT, null,
                "expected epoch does not match committed state");
    }

    /** 用于未接受的领域判断，保留原冲突、拒绝或暂不可用状态及原因。 */
    public static AddLearnerResult failure(ClusterOperationId operationId, BrokerClusterDecision decision) {
        return new AddLearnerResult(operationId.value(), BrokerClusterConsensusStatus.valueOf(decision.status().name()),
                null, decision.reason().value());
    }
}
