package com.co.kc.imchat.broker.interfaces.handler.routing;

import com.co.kc.imchat.broker.application.local.LocalLeaseAppService;
import com.co.kc.imchat.broker.model.cqrs.result.LocalLeasePreparationResult;
import com.co.kc.imchat.broker.model.cqrs.command.PrepareLocalLeaseCommand;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeasePrepareResult;

/**
 * 在目标 Broker 本机记录 Lease 请求的单调起点并返回一次性 nonce。
 */
public final class RouteLeasePrepareHandler extends
        AbstractBrokerRpcHandler<RouteLeasePrepareParams, RouteLeasePrepareResult> {

    private final LocalLeaseAppService activationService;
    private final String brokerId;

    public RouteLeasePrepareHandler(LocalLeaseAppService activationService,
                                    String brokerId) {
        super(BrokerBoltOperation.PREPARE_ROUTE_LEASE);
        if (activationService == null || brokerId == null || brokerId.isBlank()) {
            throw new IllegalArgumentException("route lease handler configuration is invalid");
        }
        this.activationService = activationService;
        this.brokerId = brokerId;
    }

    @Override
    protected RouteLeasePrepareResult process(RouteLeasePrepareParams params) {
        LocalLeasePreparationResult request = activationService.prepare(
                new PrepareLocalLeaseCommand(params.routingEpoch()));
        return new RouteLeasePrepareResult(brokerId, request.requestNonce());
    }
}
