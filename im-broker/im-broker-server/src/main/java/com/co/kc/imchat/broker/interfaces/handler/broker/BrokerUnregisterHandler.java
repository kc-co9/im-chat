package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerUnregisterParams;
import com.co.kc.imchat.broker.support.event.model.BrokerRemovedEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Broker 注销 RPC 处理器。
 * <p>
 * 接收 broker 节点注销请求，移除本地 broker 注册表记录，并发布移除事件。
 */
@Component
public class BrokerUnregisterHandler extends AbstractBrokerRpcHandler<BrokerUnregisterParams, Void> {
    private final BrokerRegistry brokerRegistry;
    private final BrokerEventPublisher brokerEventPublisher;

    public BrokerUnregisterHandler(BrokerRegistry brokerRegistry,
                                   BrokerEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.UNREGISTER_BROKER);
        this.brokerRegistry = brokerRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(BrokerUnregisterParams params) {
        brokerRegistry.unregister(params.brokerId());
        brokerEventPublisher.publish(new BrokerRemovedEvent(params.brokerId()));
        return null;
    }
}
