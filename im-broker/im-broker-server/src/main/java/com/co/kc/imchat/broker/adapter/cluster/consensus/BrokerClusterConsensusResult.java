package com.co.kc.imchat.broker.adapter.cluster.consensus;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;

/**
 * Broker 集群共识处理结果，表达提交或共识证据检查的状态、关联操作及 Leader 地址。
 */
public record BrokerClusterConsensusResult(
        /* 本次共识或证据检查的处理状态，不等于 operation 终态。 */
        Status status,
        /* 已关联的集群操作；纯证据检查或未登记失败时为空。 */
        BrokerClusterOperation operation,
        /* 当前已知的 Leader 成员通信地址；未知时为空。 */
        String leaderEndpoint,
        /* 冲突、拒绝或不可用的具体原因。 */
        String message) {

    /**
     * 集群命令处理或共识证据检查的结果，不代表 operation 终态。
     */
    public enum Status {
        /** 领域变更已应用，或共识证据检查已通过。 */
        APPLIED,
        /** 同一命令已经提交过，可安全返回先前结果。 */
        DUPLICATE,
        /** 预期 revision 或当前状态与已提交状态冲突。 */
        CONFLICT,
        /** 请求或已提交命令的领域应用不满足业务条件。 */
        REJECTED,
        /** 当前节点不是 leader，调用方应使用 leaderEndpoint 重试。 */
        NOT_LEADER,
        /** 集群当前不能提供可靠的提交结果。 */
        UNAVAILABLE
    }

    /** 当前节点不是 Leader，且附带本次读取失败的具体原因。 */
    public static BrokerClusterConsensusResult notLeader(
            String leaderEndpoint, String message) {
        return new BrokerClusterConsensusResult(Status.NOT_LEADER, null, leaderEndpoint, message);
    }

    /** 当前 Leader 暂时无法取得所需的共识证据。 */
    public static BrokerClusterConsensusResult unavailable(
            String leaderEndpoint, String message) {
        return new BrokerClusterConsensusResult(Status.UNAVAILABLE, null, leaderEndpoint, message);
    }

    /** 不同准入或证据条件均可拒绝，由调用方提供具体原因，不关联操作。 */
    public static BrokerClusterConsensusResult rejected(String message) {
        return new BrokerClusterConsensusResult(Status.REJECTED, null, null, message);
    }

    /** 证据检查已通过，可以继续提交后续集群操作。 */
    public static BrokerClusterConsensusResult applied() {
        return new BrokerClusterConsensusResult(Status.APPLIED, null, null, "learner quorum read index has been applied");
    }

    /** 共识已接受并关联指定 operation，供恢复流程继续推进已提交操作。 */
    public static BrokerClusterConsensusResult applied(
            BrokerClusterOperation operation) {
        return new BrokerClusterConsensusResult(Status.APPLIED, operation, null, null);
    }

    public boolean accepted() {
        return status == Status.APPLIED
                || status == Status.DUPLICATE;
    }

    /** 共识已接受且包含待执行的 PENDING 操作；不包含失败、已提交或无操作结果。 */
    public boolean hasPendingOperation() {
        return accepted() && operation != null
                && operation.phase() == BrokerClusterOperationPhase.PENDING;
    }

    /** 共识已接受且操作已提交拓扑，尚待推进到完成阶段。 */
    public boolean hasCommittedOperationToComplete() {
        return accepted() && operation != null
                && operation.phase() == BrokerClusterOperationPhase.RAFT_COMMITTED;
    }
}
