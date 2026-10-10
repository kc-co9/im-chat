package com.co.kc.imchat.broker.interfaces.handler.routing;

import com.co.kc.imchat.broker.application.local.LocalRouteAppService;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.DrainRouteCleanupParams;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import org.springframework.stereotype.Component;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerRoutingCommandTransformer;

/** 只接受 committed Broker 成员通过可信传输发起的 drain cleanup。 */
@Component
public final class DrainRouteCleanupHandler extends
        AbstractBrokerRpcHandler<DrainRouteCleanupParams, DrainRouteCleanupResult> {
    private final LocalRouteAppService service;

    public DrainRouteCleanupHandler(LocalRouteAppService service) {
        super(BrokerBoltOperation.CLEANUP_DRAIN_ROUTES);
        this.service = service;
    }

    @Override
    protected DrainRouteCleanupResult process(DrainRouteCleanupParams params) {
        return service.cleanupRoutes(BrokerRoutingCommandTransformer.INSTANCE.cleanupCommandFrom(params));
    }
}
