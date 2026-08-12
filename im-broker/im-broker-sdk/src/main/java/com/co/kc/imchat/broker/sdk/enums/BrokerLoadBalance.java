package com.co.kc.imchat.broker.sdk.enums;

/**
 * Broker 地址负载均衡策略。
 */
public enum BrokerLoadBalance {
    /**
     * 按请求顺序轮询 Broker 地址。
     */
    ROUND_ROBIN,

    /**
     * 随机选择 Broker 地址。
     */
    RANDOM,

    /**
     * 按业务路由键固定选择 Broker 地址。
     */
    HASH
}
