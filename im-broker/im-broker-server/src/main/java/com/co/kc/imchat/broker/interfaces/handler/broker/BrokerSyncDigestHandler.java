package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDigestParams;
import com.co.kc.imchat.plugin.gossip.model.result.GossipDigestResult;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import org.springframework.stereotype.Component;

/**
 * Broker 同步摘要处理器。
 * <p>
 * 接收其他 Broker 发起的状态摘要比对请求，并返回本机可提供的增量以及需要对方补发的 key。
 */
@Component
public class BrokerSyncDigestHandler extends AbstractBrokerRpcHandler<GossipDigestParams, GossipDigestResult> {
    private final GossipSynchronizer gossipSynchronizer;

    public BrokerSyncDigestHandler(GossipSynchronizer gossipSynchronizer) {
        super(BrokerBoltOperation.GOSSIP_DIGEST);
        this.gossipSynchronizer = gossipSynchronizer;
    }

    /**
     * 处理 peer Broker 发起的 digest 比对请求。
     *
     * @param request digest 同步参数
     * @return digest 比对结果
     */
    @Override
    protected GossipDigestResult process(GossipDigestParams request) {
        return gossipSynchronizer.handleDigest(request);
    }
}
