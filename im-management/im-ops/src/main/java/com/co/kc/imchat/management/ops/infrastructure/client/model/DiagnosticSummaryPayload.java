package com.co.kc.imchat.management.ops.infrastructure.client.model;
/** 诊断摘要协议载荷。 */
public record DiagnosticSummaryPayload(Long successCount, Long failureCount, Long lastSuccessAtMs,
                                       Long lastFailureAtMs, Long lastExecutedAtMs) { }
