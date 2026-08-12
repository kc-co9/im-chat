package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerRegisterParams;
import com.co.kc.imchat.broker.support.event.model.BrokerRegisteredEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Broker 注册 RPC 处理器。
 * <p>
 * 接收 broker 节点注册请求，写入本地 broker 注册表，并发布注册事件用于后续同步处理。
 */
@Component
public class BrokerRegisterHandler extends AbstractBrokerRpcHandler<BrokerRegisterParams, Void> {
    private final BrokerRegistry brokerRegistry;
    private final BrokerEventPublisher brokerEventPublisher;

    public BrokerRegisterHandler(BrokerRegistry brokerRegistry,
                                 BrokerEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.REGISTER_BROKER);
        this.brokerRegistry = brokerRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(BrokerRegisterParams params) {
        brokerRegistry.register(params.brokerId(), params.host(), params.port());
        brokerEventPublisher.publish(new BrokerRegisteredEvent(params.brokerId(), params.host(), params.port()));
        return null;
    }
}
