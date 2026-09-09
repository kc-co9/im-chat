package com.co.kc.imchat.management.monitor.model.io;

/** 诊断动作累计摘要响应。 */
public record DiagnosticSummaryResponse(Long successCount, Long failureCount,
                                        Long lastSuccessAt, Long lastFailureAt,
                                        Long lastExecutedAt) {
}
