package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import java.time.Instant;

/** 节点诊断动作累计摘要。 */
public record DiagnosticSummaryDTO(
        Long successCount,
        Long failureCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        Instant lastExecutedAt
) {
}
