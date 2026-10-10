package com.co.kc.imchat.broker.interfaces.handler.gateway;

import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import com.co.kc.imchat.broker.model.cqrs.event.GatewayRegisteredEvent;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import org.springframework.stereotype.Component;

/**
 * 网关注册 RPC 处理器。
 */
@Component
public class GatewayRegisterHandler extends AbstractBrokerRpcHandler<GatewayRegisterParams, Void> {
    private final GatewayRegistry gatewayRegistry;
    private final DomainEventPublisher brokerEventPublisher;

    public GatewayRegisterHandler(GatewayRegistry gatewayRegistry,
                                  DomainEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.REGISTER_GATEWAY);
        this.gatewayRegistry = gatewayRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void invoke(GatewayRegisterParams params, BrokerCallerPrincipal principal) {
        if (principal == null || (principal.callerRole() != BrokerCallerRole.GATEWAY
                && principal.callerRole() != BrokerCallerRole.TRUSTED_NETWORK)) {
            throw new SecurityException("required gateway caller is missing");
        }
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom(
                params.gatewayId(), params.host(), params.port(), principal.callerIdentity()));
        brokerEventPublisher.publish(new GatewayRegisteredEvent(
                params.gatewayId(), params.host(), params.port(), principal.callerIdentity()));
        return null;
    }

    @Override
    protected Void process(GatewayRegisterParams params) {
        throw new SecurityException("gateway registration requires authenticated principal");
    }
}
