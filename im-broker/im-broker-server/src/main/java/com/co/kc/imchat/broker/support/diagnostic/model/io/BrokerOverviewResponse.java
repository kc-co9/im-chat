package com.co.kc.imchat.broker.support.diagnostic.model.io;

/** Broker 节点总览响应。 */
public record BrokerOverviewResponse(
        /* 当前 Broker 实例。 */
        BrokerInstanceResponse broker,
        /* 集群注册状态统计。 */
        BrokerStatisticsResponse statistics,
        /* Gossip 执行摘要。 */
        DiagnosticSummaryResponse gossip,
        /* 连接迁移执行摘要。 */
        DiagnosticSummaryResponse migration
) {
}
