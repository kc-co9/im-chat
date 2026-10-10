package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.adapter.cluster.LocalBroker;
import com.co.kc.imchat.broker.sdk.model.result.QuorumReadIndexResult;

import java.util.concurrent.CompletionStage;

/**
 * 查询本机 Broker 已应用的 quorum ReadIndex，身份由启动装配固定，不接受请求伪造。
 */
public final class BrokerQuorumAppService {
    private final BrokerClusterAdapter clusterAdapter;

    public BrokerQuorumAppService(BrokerClusterAdapter clusterAdapter) {
        this.clusterAdapter = clusterAdapter;
    }

    /**
     * 等本机状态机应用到 quorum ReadIndex 后，返回进度和本机身份；失败原样传播。
     */
    public CompletionStage<QuorumReadIndexResult> fetchQuorumReadIndex() {
        LocalBroker broker = clusterAdapter.getLocalBroker();
        return broker.fetchQuorumReadIndex().thenApply(index -> new QuorumReadIndexResult(broker.nodeId(), index));
    }
}
