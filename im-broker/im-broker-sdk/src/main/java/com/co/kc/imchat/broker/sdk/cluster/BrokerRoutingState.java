package com.co.kc.imchat.broker.sdk.cluster;

/**
 * Broker 参与连接路由的权威状态。
 */
public enum BrokerRoutingState {
    INACTIVE,
    READY,
    ACTIVE,
    DRAINING
}
