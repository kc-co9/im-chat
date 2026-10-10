package com.co.kc.imchat.broker.interfaces.handler.routing;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.authority.model.LocalFenceResult;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.broker.sdk.model.result.RoutingFenceAckResult;

/**
 * 在返回 ACK 前关闭本机 admission、等待在途执行并持久化 fence marker。
 */
public final class RoutingFenceHandler extends
        AbstractBrokerRpcHandler<RoutingFenceParams, RoutingFenceAckResult> {

    private final LocalRoutingLeaseService leaseManager;
    private final String brokerId;

    public RoutingFenceHandler(LocalRoutingLeaseService leaseManager,
                               String brokerId) {
        super(BrokerBoltOperation.FENCE_ROUTING_EPOCH);
        if (leaseManager == null || brokerId == null || brokerId.isBlank()) {
            throw new IllegalArgumentException("routing fence handler configuration is invalid");
        }
        this.leaseManager = leaseManager;
        this.brokerId = brokerId;
    }

    @Override
    protected RoutingFenceAckResult process(RoutingFenceParams params) {
        LocalFenceResult result = leaseManager.fenceLocal(
                new RoutingEpoch(params.routingEpoch()));
        return new RoutingFenceAckResult(brokerId, params.routingEpoch(),
                params.leaderTerm(), result.fencedLeaseSequence().value(), params.operationId());
    }
}
