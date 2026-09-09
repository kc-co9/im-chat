package com.co.kc.imchat.management.monitor.infrastructure.client.model;
import java.time.Instant;
/** 诊断摘要协议载荷。 */
public record DiagnosticSummaryPayload(Long successCount, Long failureCount, Instant lastSuccessAt,
                                       Instant lastFailureAt, Instant lastExecutedAt) { }
