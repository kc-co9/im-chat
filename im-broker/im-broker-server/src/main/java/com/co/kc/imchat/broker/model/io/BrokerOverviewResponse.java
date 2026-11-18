package com.co.kc.imchat.broker.model.io;

/** Broker 节点总览响应。 */
public record BrokerOverviewResponse(
        /* 当前 Broker 实例。 */
        BrokerInstanceResponse broker,
        /* 集群注册状态统计。 */
        BrokerStatisticsResponse statistics,
        /* Gossip 执行摘要。 */
        DiagnosticSummaryResponse gossip
) {
}
