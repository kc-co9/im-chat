package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDeltaParams;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import org.springframework.stereotype.Component;

/**
 * Broker 同步增量处理器。
 * <p>
 * 接收其他 Broker 推送过来的增量状态，并委托通用 Gossip 同步器合并到本机状态。
 */
@Component
public class BrokerSyncDeltaHandler extends AbstractBrokerRpcHandler<GossipDeltaParams, Void> {
    private final GossipSynchronizer gossipSynchronizer;

    public BrokerSyncDeltaHandler(GossipSynchronizer gossipSynchronizer) {
        super(BrokerBoltOperation.GOSSIP_DELTA);
        this.gossipSynchronizer = gossipSynchronizer;
    }

    /**
     * 处理 peer Broker 推送的 delta 数据。
     *
     * @param request delta 同步参数
     * @return 无返回值
     */
    @Override
    protected Void process(GossipDeltaParams request) {
        gossipSynchronizer.handleDelta(request);
        return null;
    }
}
