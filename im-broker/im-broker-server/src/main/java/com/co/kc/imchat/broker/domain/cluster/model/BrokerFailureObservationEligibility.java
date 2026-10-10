package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 多个 Broker 健康观察是否满足自动故障摘流前置条件的领域判定。
 *
 * <p>{@link #ELIGIBLE} 只表示观察证据足以进入后续 fencing 流程，不代表目标节点已经被
 * 移出拓扑；{@link #INELIGIBLE} 表示仍需等待更多独立观察、稳定窗口或冷却窗口。</p>
 */
public enum BrokerFailureObservationEligibility {
    ELIGIBLE,
    INELIGIBLE
}
