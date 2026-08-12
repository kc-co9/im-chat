package com.co.kc.imchat.broker.sdk.enums;

/**
 * Broker 实时帧流向。
 */
public enum BrokerFrameDirection {
    /**
     * 客户端上行到业务服务。
     */
    INBOUND,

    /**
     * 业务服务下行到客户端连接。
     */
    OUTBOUND
}
