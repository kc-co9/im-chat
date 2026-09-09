package com.co.kc.imchat.management.monitor.model.io;

/** Broker 管理总览响应。 */
public record BrokerOverviewDataResponse(BrokerInstanceResponse broker,
                                         BrokerStatisticsResponse statistics,
                                         DiagnosticSummaryResponse gossip,
                                         DiagnosticSummaryResponse migration) {
}
