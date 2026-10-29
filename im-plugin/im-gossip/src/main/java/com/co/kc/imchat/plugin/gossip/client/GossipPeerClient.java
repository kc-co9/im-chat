package com.co.kc.imchat.plugin.gossip.client;

import com.co.kc.imchat.plugin.bolt.client.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDeltaParams;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDigestParams;
import com.co.kc.imchat.plugin.gossip.model.result.GossipDigestResult;

/**
 * Gossip 节点间同步客户端。
 * <p>
 * 封装 gossip digest/delta 两类远程调用，具体的 service 与 operation 由业务模块传入。
 */
public class GossipPeerClient {
    private final BoltRpcClient boltRpcClient;

    public GossipPeerClient(BoltRpcClient boltRpcClient) {
        this.boltRpcClient = boltRpcClient;
    }

    /**
     * 向指定 peer 发送本地状态摘要，并获取双方状态差异。
     *
     * @param target        peer 地址与完整身份
     * @param service       Gossip RPC 服务名
     * @param operation     摘要交换操作名
     * @param params        本地节点及状态摘要
     * @param timeoutMillis RPC 调用超时时间，单位为毫秒
     * @return peer 返回的增量数据及待推送键
     */
    public GossipDigestResult exchangeDigest(BoltTarget target,
                                             String service,
                                             String operation,
                                             GossipDigestParams params,
                                             int timeoutMillis) {
        return boltRpcClient.invoke(target, service, operation, params, GossipDigestResult.class, timeoutMillis);
    }

    /**
     * 向指定 peer 推送本地较新的增量数据。
     *
     * @param target        peer 地址与完整身份
     * @param service       Gossip RPC 服务名
     * @param operation     增量推送操作名
     * @param params        本地节点及增量数据
     * @param timeoutMillis RPC 调用超时时间，单位为毫秒
     */
    public void pushDeltas(BoltTarget target,
                           String service,
                           String operation,
                           GossipDeltaParams params,
                           int timeoutMillis) {
        boltRpcClient.invoke(target, service, operation, params, Void.class, timeoutMillis);
    }
}
