package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerQueryParams;
import org.springframework.stereotype.Component;

/**
 * 返回本 Broker 已安装的完整 committed cluster state 快照。
 */
@Component
public final class BrokerClusterStateHandler extends
        AbstractBrokerRpcHandler<BrokerQueryParams, BrokerClusterState> {

    private final BrokerRaftAdapter repository;

    public BrokerClusterStateHandler(BrokerRaftAdapter repository) {
        super(BrokerBoltOperation.GET_CLUSTER_STATE);
        this.repository = repository;
    }

    @Override
    protected BrokerClusterState process(BrokerQueryParams params) {
        return repository.getCommittedState();
    }
}
