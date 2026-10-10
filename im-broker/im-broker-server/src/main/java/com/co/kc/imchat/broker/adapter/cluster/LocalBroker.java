package com.co.kc.imchat.broker.adapter.cluster;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.plugin.raft.core.RaftRuntime;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

/**
 * 本机 Broker 的 Raft 查询入口，复用集群 Adapter 持有的运行实例，不保存额外状态。
 * 本机角色、已知 Leader 地址和本机应用进度从运行实例读取；集群提交和成员变更不在此入口执行。
 * 运行实例由 BrokerClusterAdapter 负责关闭，本类不拥有独立生命周期。
 */
public final class LocalBroker {
    /* 集群 Adapter 所拥有的本机 Raft 运行实例。 */
    private final RaftRuntime<BrokerClusterConsensusResult> runtime;

    LocalBroker(RaftRuntime<BrokerClusterConsensusResult> runtime) {
        this.runtime = runtime;
    }

    /** 查询本机当前角色；可能随后换届，不代替多数派领导权确认。 */
    public boolean isLeader() {
        return runtime.isLeader();
    }

    /** 返回本机 Broker 对应的稳定 Raft 节点身份。 */
    public String nodeId() {
        return runtime.nodeId();
    }

    /** 查询本机当前已知的集群 Leader 成员通信地址；未知时为空。 */
    public String leaderEndpoint() {
        return runtime.leaderEndpoint();
    }

    /** 获取 quorum ReadIndex 并等待本机状态机应用到该位置，失败按原异步契约传播。 */
    public CompletionStage<Long> fetchQuorumReadIndex() {
        return runtime.fetchQuorumReadIndex(GeneratorUtils.nextUUID().getBytes(StandardCharsets.UTF_8));
    }
}
