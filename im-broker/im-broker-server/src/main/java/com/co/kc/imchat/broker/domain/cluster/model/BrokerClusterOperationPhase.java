package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 集群 operation 的可恢复、单调生命周期阶段。
 *
 * <p>阶段表达从登记变更、隔离旧 epoch、提交新拓扑、授权新 epoch、重建路由到证据核对
 * 的业务过程。除显式允许的失败、降级和强制分支外，阶段只能向前推进；终态不会再次
 * 执行拓扑或路由副作用。</p>
 */
public enum BrokerClusterOperationPhase {
    /** 变更意图已登记，尚未完成拓扑提交。 */
    PENDING,
    /** 正隔离旧 epoch 的写入与执行。 */
    FENCING_OLD_EPOCH,
    /** 新拓扑已经 Raft 提交。 */
    RAFT_COMMITTED,
    /** 正向新的 RouteBroker 持有者授权。 */
    AUTHORIZING_NEW_EPOCH,
    /** 正重建连接路由并收集 Gateway 快照。 */
    REBUILDING_ROUTES,
    /** 正核对旧事件与旧路由清理证据。 */
    VERIFYING,
    /** 所有必要步骤完成。 */
    SUCCEEDED,
    /** 可继续服务，但恢复证据存在明确缺口。 */
    DEGRADED,
    /** 操作失败，需要恢复或人工介入。 */
    FAILED,
    /** 管理员显式接受残留风险后强制结束。 */
    FORCED;

    public boolean terminal() {
        return this == SUCCEEDED || this == DEGRADED || this == FAILED || this == FORCED;
    }

    /** 是否允许继续排空编排，包括重建、验证恢复和完成重试；操作类型仍由调用方确认。 */
    public boolean canAdvanceDrain() {
        return this == REBUILDING_ROUTES || this == VERIFYING || this == SUCCEEDED;
    }
}
