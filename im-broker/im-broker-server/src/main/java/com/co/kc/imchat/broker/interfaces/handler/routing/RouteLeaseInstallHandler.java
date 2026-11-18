package com.co.kc.imchat.broker.interfaces.handler.routing;

import com.co.kc.imchat.broker.application.local.LocalLeaseAppService;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptLocalLeaseCommand;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeaseInstallResult;
import org.springframework.stereotype.Component;

/**
 * 只接受与本机 pending nonce 匹配且仍在保守 deadline 内的 Lease。
 */
@Component
public final class RouteLeaseInstallHandler extends
        AbstractBrokerRpcHandler<RouteLeaseInstallParams, RouteLeaseInstallResult> {

    private final LocalLeaseAppService activationService;

    public RouteLeaseInstallHandler(LocalLeaseAppService activationService) {
        super(BrokerBoltOperation.INSTALL_ROUTE_LEASE);
        this.activationService = activationService;
    }

    @Override
    protected RouteLeaseInstallResult process(RouteLeaseInstallParams params) {
        return new RouteLeaseInstallResult(
                activationService.accept(new AcceptLocalLeaseCommand(
                        params.brokerId(),
                        params.routingEpoch(),
                        params.leaderTerm(),
                        params.leaseSequence(),
                        params.requestNonce(),
                        params.validForNanos(),
                        params.authorityReadIndex())));
    }
}
