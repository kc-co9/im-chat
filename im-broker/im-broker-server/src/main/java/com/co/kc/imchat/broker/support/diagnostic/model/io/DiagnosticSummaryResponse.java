package com.co.kc.imchat.broker.support.diagnostic.model.io;

import java.time.Instant;

/** 诊断动作累计摘要响应。 */
public record DiagnosticSummaryResponse(
        /* 成功次数。 */
        Long successCount,
        /* 失败次数。 */
        Long failureCount,
        /* 最后成功时间。 */
        Instant lastSuccessAt,
        /* 最后失败时间。 */
        Instant lastFailureAt,
        /* 最后执行时间。 */
        Instant lastExecutedAt
) {
}
