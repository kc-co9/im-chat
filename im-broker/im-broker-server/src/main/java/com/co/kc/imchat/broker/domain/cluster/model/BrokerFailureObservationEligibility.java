package com.co.kc.imchat.broker.domain.cluster.model;



/**
 * 多个 Broker 健康观察是否满足故障报告证据条件的领域判定。
 *
 * <p>{@link #ELIGIBLE} 只表示观察证据充分，不包含冷却或自动摘流授权，也不代表目标节点已被
 * 移出拓扑；{@link #INELIGIBLE} 表示独立报告数量、新鲜度或稳定窗口尚不满足要求。</p>
 */
public enum BrokerFailureObservationEligibility {
    ELIGIBLE,
    INELIGIBLE
}
