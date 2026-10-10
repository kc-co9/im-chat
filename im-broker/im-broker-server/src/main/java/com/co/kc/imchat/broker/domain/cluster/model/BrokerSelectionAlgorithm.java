package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 建群时确定的 RouteBroker 选择算法。
 *
 * <p>算法决定用户如何从 ACTIVE Broker 集合中得到稳定的路由持有者。它属于集群长期
 * 业务事实，不能在已有拓扑运行期间随意切换，否则会改变路由映射并触发新的 epoch。</p>
 */
public enum BrokerSelectionAlgorithm {
    /** 对每个 Broker 计算最高随机权重，取分数最高者。 */
    RENDEZVOUS_HASH,
    /** 按用户哈希对 Broker 数量取模。 */
    MODULO_HASH
}
