package com.co.kc.imchat.broker.model.cqrs.dto;

/**
 * Broker 节点诊断总览。
 */
public record BrokerOverviewDTO(
        /* 当前 Broker 实例。 */
        BrokerInstanceDTO broker,
        /* 集群注册状态统计。 */
        BrokerStatisticsDTO statistics,
        /* Gossip 执行摘要。 */
        DiagnosticSummaryDTO gossip
) {
}
