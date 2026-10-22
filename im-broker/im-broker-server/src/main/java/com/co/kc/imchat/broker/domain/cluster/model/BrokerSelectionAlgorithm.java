package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 建群时确定的 RouteBroker 选择算法。
 *
 * <p>算法决定用户如何从 ACTIVE Broker 集合中得到稳定的路由持有者。它属于集群长期
 * 业务事实，当前不支持在集群运行期间切换。</p>
 */
public enum BrokerSelectionAlgorithm {
    /** 对每个 Broker 计算最高随机权重，取分数最高者。 */
    RENDEZVOUS_HASH,
    /** 按用户哈希对 Broker 数量取模。 */
    MODULO_HASH
}
