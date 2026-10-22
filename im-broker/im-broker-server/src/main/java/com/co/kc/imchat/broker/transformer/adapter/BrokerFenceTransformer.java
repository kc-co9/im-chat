package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.authority.model.LeaseSequence;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.broker.sdk.model.result.RoutingFenceAckResult;

/** 领域 fence 事实与 Broker peer RPC 载荷之间的转换，不判断 ACK 是否满足隔离条件。 */
public final class BrokerFenceTransformer {
    public static final BrokerFenceTransformer INSTANCE = new BrokerFenceTransformer();

    private BrokerFenceTransformer() {
    }

    public RoutingFenceParams routingFenceParamsFrom(RoutingEpochFence fence) {
        return new RoutingFenceParams(fence.operationId().value(), fence.epoch().value(),
                fence.leaderTerm().value(), fence.quorumReadIndex().value());
    }

    public FenceAck fenceAckFrom(RoutingFenceAckResult response) {
        return FenceAck.builder()
                .brokerId(new BrokerId(response.brokerId()))
                .routingEpoch(new RoutingEpoch(response.routingEpoch()))
                .leaderTerm(new LeaderTerm(response.leaderTerm()))
                .fencedLeaseSequence(new LeaseSequence(response.lastLeaseSequence()))
                .operationId(new ClusterOperationId(response.operationId()))
                .build();
    }
}
