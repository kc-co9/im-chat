package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerQueryParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerListResult;
import org.springframework.stereotype.Component;

/**
 * Broker 列表查询 RPC 处理器。
 * <p>
 * 向网关或其他 broker 暴露当前 broker 可见的集群节点列表。
 */
@Component
public class BrokerListHandler extends AbstractBrokerRpcHandler<BrokerQueryParams, BrokerListResult> {
    private final BrokerRegistry brokerRegistry;

    public BrokerListHandler(BrokerRegistry brokerRegistry) {
        super(BrokerBoltOperation.LIST_BROKERS);
        this.brokerRegistry = brokerRegistry;
    }

    @Override
    protected BrokerListResult process(BrokerQueryParams params) {
        return new BrokerListResult(brokerRegistry.list());
    }
}
