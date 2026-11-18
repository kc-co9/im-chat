package com.co.kc.imchat.broker.model.io;


/** 诊断动作累计摘要响应。 */
public record DiagnosticSummaryResponse(
        /* 成功次数。 */
        Long successCount,
        /* 失败次数。 */
        Long failureCount,
        /* 最后成功时间。 */
        Long lastSuccessAtMs,
        /* 最后失败时间。 */
        Long lastFailureAtMs,
        /* 最后执行时间。 */
        Long lastExecutedAtMs
) {
}
