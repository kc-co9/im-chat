package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.model.cqrs.event.BrokerHeartbeatEvent;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHeartbeatParams;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Broker 心跳 RPC 处理器。
 * <p>
 * 接收其他 broker 的心跳请求，刷新本地 broker 注册表，并发布心跳事件用于同步 gossip 状态。
 */
@Component
public class BrokerHeartbeatHandler extends AbstractBrokerRpcHandler<BrokerHeartbeatParams, Void> {
    private final BrokerRegistry brokerRegistry;
    private final DomainEventPublisher brokerEventPublisher;

    public BrokerHeartbeatHandler(BrokerRegistry brokerRegistry,
                                  DomainEventPublisher brokerEventPublisher) {
        super(BrokerBoltOperation.HEARTBEAT_BROKER);
        this.brokerRegistry = brokerRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
    }

    @Override
    protected Void process(BrokerHeartbeatParams params) {
        brokerRegistry.heartbeat(params.brokerId());
        brokerEventPublisher.publish(new BrokerHeartbeatEvent(params.brokerId()));
        return null;
    }
}
