package com.co.kc.imchat.broker.interfaces.handler.gateway;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.support.event.model.GatewayHeartbeatEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 网关心跳 RPC 处理器。
 */
@Component
public class GatewayHeartbeatHandler extends AbstractBrokerRpcHandler<GatewayHeartbeatParams, Void> {
    private final GatewayRegistry gatewayRegistry;
    private final BrokerEventPublisher brokerEventPublisher;

    public GatewayHeartbeatHandler(GatewayRegistry gatewayRegistry,
                                   BrokerEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.HEARTBEAT_GATEWAY);
        this.gatewayRegistry = gatewayRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(GatewayHeartbeatParams params) {
        if (params == null || params.gatewayId() == null || params.gatewayId().isBlank()) {
            return null;
        }
        gatewayRegistry.heartbeat(params.gatewayId());
        brokerEventPublisher.publish(new GatewayHeartbeatEvent(params.gatewayId()));
        return null;
    }
}
