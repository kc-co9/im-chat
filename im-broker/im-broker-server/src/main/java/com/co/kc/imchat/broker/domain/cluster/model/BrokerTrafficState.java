package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * Broker 在用户路由选择和流量生命周期中的领域状态。
 *
 * <p>该状态描述节点是否可以成为 RouteBroker，以及从准备、激活到 drain 的流量迁移过程。
 * 它独立于 {@link BrokerMembershipRole}：一个节点可以已经是 voter，但仍处于 READY、
 * INACTIVE 或 DRAINING，不能因此直接承载新用户路由。</p>
 */
public enum BrokerTrafficState {
    /** 不参与用户路由选择。 */
    INACTIVE,
    /** 已具备服务条件，等待显式激活。 */
    READY,
    /** 可以被选为 RouteBroker。 */
    ACTIVE,
    /** 正摘除新流量并清理旧路由。 */
    DRAINING
}
