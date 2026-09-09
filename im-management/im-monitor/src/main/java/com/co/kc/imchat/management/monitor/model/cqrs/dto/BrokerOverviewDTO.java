package com.co.kc.imchat.management.monitor.model.cqrs.dto;

/** Broker 节点管理接口返回的总览数据。 */
public record BrokerOverviewDTO(
        BrokerInstanceDTO broker,
        BrokerStatisticsDTO statistics,
        DiagnosticSummaryDTO gossip,
        DiagnosticSummaryDTO migration) {
}
