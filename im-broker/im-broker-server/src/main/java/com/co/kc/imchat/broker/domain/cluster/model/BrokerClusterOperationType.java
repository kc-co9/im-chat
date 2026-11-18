package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 权威集群 operation 的业务意图枚举。
 *
 * <p>该类型区分成员配置变化、路由流量状态变化和故障恢复流程。BrokerCluster 根据意图
 * 校验目标节点当前角色和业务前置条件，并在 Commit 执行具名行为；{@link #DRAIN_BROKER} 与
 * {@link #FAIL_BROKER} 还必须携带 drain 证据。</p>
 */
public enum BrokerClusterOperationType {
    /**
     * 把发现的节点加入拓扑。
     */
    ADD_BROKER,
    /**
     * 将已发现节点加入 Raft learner。
     */
    ADD_LEARNER,
    /**
     * 启动 learner 的日志追赶。
     */
    START_LEARNER_SYNC,
    /**
     * 确认 learner 已就绪。
     */
    COMPLETE_LEARNER_SYNC,
    /**
     * 将 learner 转为 voter 的拓扑意图。
     */
    PROMOTE_LEARNER,
    /**
     * 提交 Broker 提升为 voter 的状态。
     */
    PROMOTE_BROKER,
    /**
     * 将 voter 转为 learner 的拓扑意图。
     */
    DEMOTE_VOTER,
    /**
     * 提交 Broker 降级为 learner 的状态。
     */
    DEMOTE_BROKER,
    /**
     * 移除已停止承载流量的成员。
     */
    REMOVE_BROKER,
    /**
     * 激活 Broker 参与用户路由。
     */
    ACTIVATE_BROKER,
    /**
     * 将 Broker 从 RouteBroker 选择中停用。
     */
    DEACTIVATE_BROKER,
    /**
     * 有序摘流并重建旧路由。
     */
    DRAIN_BROKER,
    /**
     * 故障摘流并恢复路由。
     */
    FAIL_BROKER;

    /**
     * 该意图是否涉及共识成员的加入、就绪、角色变化或移除。
     */
    public boolean membershipChange() {
        return switch (this) {
            case ADD_LEARNER, START_LEARNER_SYNC, COMPLETE_LEARNER_SYNC, PROMOTE_LEARNER,
                 PROMOTE_BROKER, DEMOTE_VOTER, DEMOTE_BROKER, REMOVE_BROKER -> true;
            default -> false;
        };
    }

    public boolean routeRecovery() {
        return this == DRAIN_BROKER || this == FAIL_BROKER;
    }
}
