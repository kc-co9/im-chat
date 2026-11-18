package com.co.kc.imchat.broker.interfaces.handler.gateway;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.application.local.LocalGatewayCleanupAppService;

import com.co.kc.imchat.broker.model.cqrs.event.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网关注销 RPC 处理器。
 */
@Component
public class GatewayUnregisterHandler extends AbstractBrokerRpcHandler<GatewayUnregisterParams, Void> {
    private final LocalGatewayCleanupAppService gatewayCleanup;
    private final DomainEventPublisher brokerEventPublisher;

    public GatewayUnregisterHandler(LocalGatewayCleanupAppService gatewayCleanup,
                                    DomainEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.UNREGISTER_GATEWAY);
        this.gatewayCleanup = gatewayCleanup;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(GatewayUnregisterParams params) {
        if (params == null || params.gatewayId() == null || params.gatewayId().isBlank()) {
            return null;
        }
        List<ConnectionRegistration> removedCollections = gatewayCleanup.unregister(new GatewayId(params.gatewayId()));
        brokerEventPublisher.publish(
                new ConnectionSyncedEvent(params.gatewayId(), List.of(), removedCollections));
        return null;
    }
}
