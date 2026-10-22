package com.co.kc.imchat.broker.domain.cluster.model;



/**
 * 健康观察值状态；UNKNOWN 不提供不可达证据，也不继承旧失败窗口。
 */
public enum BrokerHealthStatus {
    HEALTHY,
    UNHEALTHY,
    UNKNOWN
}
