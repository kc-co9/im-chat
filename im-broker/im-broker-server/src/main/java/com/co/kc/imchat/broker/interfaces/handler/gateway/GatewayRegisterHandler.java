package com.co.kc.imchat.broker.interfaces.handler.gateway;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.support.event.model.GatewayRegisteredEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 网关注册 RPC 处理器。
 */
@Component
public class GatewayRegisterHandler extends AbstractBrokerRpcHandler<GatewayRegisterParams, Void> {
    private final GatewayRegistry gatewayRegistry;
    private final BrokerEventPublisher brokerEventPublisher;

    public GatewayRegisterHandler(GatewayRegistry gatewayRegistry,
                                  BrokerEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.REGISTER_GATEWAY);
        this.gatewayRegistry = gatewayRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(GatewayRegisterParams params) {
        gatewayRegistry.register(params.gatewayId(), params.host(), params.port());
        brokerEventPublisher.publish(new GatewayRegisteredEvent(params.gatewayId(), params.host(), params.port()));
        return null;
    }
}
