package com.co.kc.imchat.broker.support.diagnostic.model.dto;

/**
 * 当前 Broker 注册状态统计。
 */
public record BrokerStatisticsDTO(
        /* Broker 数量。 */
        Long brokerCount,
        /* Gateway 数量。 */
        Long gatewayCount,
        /* 用户连接路由数量。 */
        Long connectionCount
) {
}
