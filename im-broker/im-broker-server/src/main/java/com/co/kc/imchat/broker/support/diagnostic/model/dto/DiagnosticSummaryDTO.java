package com.co.kc.imchat.broker.support.diagnostic.model.dto;

import java.time.Instant;

/**
 * 诊断动作的进程内累计执行摘要。
 *
 * @param successCount  成功次数
 * @param failureCount  失败次数
 * @param lastSuccessAt 最后成功时间
 * @param lastFailureAt 最后失败时间
 * @param lastExecutedAt 最后执行时间
 */
public record DiagnosticSummaryDTO(
        Long successCount,
        Long failureCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        Instant lastExecutedAt
) {
}
