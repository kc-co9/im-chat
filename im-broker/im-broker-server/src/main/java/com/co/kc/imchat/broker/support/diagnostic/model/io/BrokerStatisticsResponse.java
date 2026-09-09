package com.co.kc.imchat.broker.support.diagnostic.model.io;

/** Broker 注册状态统计响应。 */
public record BrokerStatisticsResponse(
        /* Broker 数量。 */
        Long brokerCount,
        /* Gateway 数量。 */
        Long gatewayCount,
        /* 用户连接路由数量。 */
        Long connectionCount
) {
}
