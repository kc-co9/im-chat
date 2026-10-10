package com.co.kc.imchat.broker.sdk.model.result;

/**
 * RouteBroker 在产生路由副作用前可返回的确定结果。
 */
public enum RouteBrokerStatus {
    EXECUTED,
    ROUTE_MOVED,
    BROKER_STATE_STALE,
    ROUTE_MISMATCH,
    ROUTING_FENCED
}
