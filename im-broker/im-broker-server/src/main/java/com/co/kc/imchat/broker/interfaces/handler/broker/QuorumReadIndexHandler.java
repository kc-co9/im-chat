package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.application.cluster.BrokerQuorumAppService;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.result.QuorumReadIndexResult;
import org.springframework.stereotype.Component;


/** 仅在本机 Raft 状态机应用到 quorum ReadIndex 后返回。 */
@Component
public final class QuorumReadIndexHandler extends
        AbstractBrokerRpcHandler<QuorumReadIndexParams, QuorumReadIndexResult> {
    private final BrokerQuorumAppService brokerQuorumAppService;

    public QuorumReadIndexHandler(BrokerQuorumAppService brokerQuorumAppService) {
        super(BrokerBoltOperation.FETCH_QUORUM_READ_INDEX);
        this.brokerQuorumAppService = brokerQuorumAppService;
    }

    @Override
    protected QuorumReadIndexResult process(QuorumReadIndexParams ignored) {
        return brokerQuorumAppService.fetchQuorumReadIndex().toCompletableFuture().join();
    }
}
