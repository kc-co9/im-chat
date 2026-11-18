package com.co.kc.imchat.broker.interfaces.handler.connection;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotAckParams;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import org.springframework.stereotype.Component;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerRoutingCommandTransformer;

/** 接收经过 mTLS 身份绑定的 Gateway 整轮快照确认。 */
@Component
public final class GatewayRouteSnapshotAckHandler extends
        AbstractBrokerRpcHandler<GatewayRouteSnapshotAckParams,
                GatewayRouteSnapshotAckResult> {
    private final BrokerClusterAppService service;

    public GatewayRouteSnapshotAckHandler(BrokerClusterAppService service) {
        super(BrokerBoltOperation.ACK_GATEWAY_ROUTE_SNAPSHOT);
        this.service = service;
    }

    @Override
    protected GatewayRouteSnapshotAckResult process(GatewayRouteSnapshotAckParams params) {
        return service.confirmGatewayRoutes(BrokerRoutingCommandTransformer.INSTANCE.confirmGatewayRoutesCommandFrom(params));
    }
}
