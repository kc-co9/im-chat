package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.broker.application.local.LocalRouteAppService;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotParams;
import com.co.kc.imchat.broker.sdk.model.result.AcceptGatewayRoutesResult;
import org.springframework.stereotype.Component;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerRoutingCommandTransformer;

/** 接收经过 mTLS 身份上下文认证的 Gateway 路由快照分块。 */
@Component
public final class GatewayRouteSnapshotHandler extends
        AbstractBrokerRpcHandler<GatewayRouteSnapshotParams, AcceptGatewayRoutesResult> {
    private final LocalRouteAppService service;

    public GatewayRouteSnapshotHandler(LocalRouteAppService service) {
        super(BrokerBoltOperation.APPLY_GATEWAY_ROUTE_SNAPSHOT);
        this.service = service;
    }

    @Override
    protected AcceptGatewayRoutesResult process(GatewayRouteSnapshotParams params) throws Exception {
        return service.acceptRoutes(BrokerRoutingCommandTransformer.INSTANCE.acceptGatewayRoutesCommandFrom(params));
    }
}
