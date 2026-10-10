package com.co.kc.imchat.plugin.raft.client;

import com.co.kc.imchat.plugin.bolt.core.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;

/**
 * Raft 使用方的节点间应用进度查询客户端，通过 Bolt 调用目标 peer 的读取证明接口。
 * 不处理 JRaft 原生选举、日志复制或快照传输；协议名称、载荷类型与业务身份校验由使用方负责。
 */
public final class RaftPeerClient {
    /* 共用 Bolt 传输能力，不持有业务模块模型。 */
    private final BoltRpcClient rpcClient;

    public RaftPeerClient(BoltInvoker invoker) {
        this.rpcClient = new BoltRpcClient(invoker);
    }

    /**
     * 查询目标 peer 已取得并应用的 quorum ReadIndex 证明，原样返回使用方的响应类型。
     *
     * @param address 目标 peer 的 RPC 地址，不是 Raft 原生成员端点
     * @param service 使用方定义的 RPC 服务名称
     * @param operation 使用方定义的应用 ReadIndex 查询操作
     * @param params 使用方定义的请求载荷
     * @param responseType 使用方定义的响应类型
     * @param timeoutMillis RPC 超时毫秒数
     * @return peer 返回的证明载荷，不代表该节点最新应用位置
     */
    public <P, R> R fetchQuorumReadIndex(String address, String service, String operation,
                                        P params, Class<R> responseType, int timeoutMillis) {
        return rpcClient.invoke(address, service, operation, params, responseType, timeoutMillis);
    }
}
