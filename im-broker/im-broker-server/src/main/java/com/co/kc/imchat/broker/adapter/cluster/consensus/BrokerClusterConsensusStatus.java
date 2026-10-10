package com.co.kc.imchat.broker.adapter.cluster.consensus;

/** Raft 提交 Broker 集群命令后的协议结果，不代表操作的终态。 */
public enum BrokerClusterConsensusStatus {
    /** 命令已提交并改变集群状态。 */
    APPLIED,
    /** 同一命令已经提交过，可安全返回先前结果。 */
    DUPLICATE,
    /** 预期 revision 或当前状态与已提交状态冲突。 */
    CONFLICT,
    /** 命令不满足集群不变量，不能提交。 */
    REJECTED,
    /** 当前节点不是 leader，调用方应使用 leaderEndpoint 重试。 */
    NOT_LEADER,
    /** 集群当前不能提供可靠的提交结果。 */
    UNAVAILABLE
}
