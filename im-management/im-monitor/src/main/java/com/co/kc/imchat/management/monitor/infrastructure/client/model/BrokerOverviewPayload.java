package com.co.kc.imchat.management.monitor.infrastructure.client.model;
/** Broker 总览协议载荷。 */
public record BrokerOverviewPayload(BrokerInstancePayload broker, BrokerStatisticsPayload statistics,
                                    DiagnosticSummaryPayload gossip, DiagnosticSummaryPayload migration) { }
