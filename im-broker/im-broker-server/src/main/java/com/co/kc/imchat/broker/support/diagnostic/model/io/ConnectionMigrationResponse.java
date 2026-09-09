package com.co.kc.imchat.broker.support.diagnostic.model.io;

import com.co.kc.imchat.broker.support.diagnostic.model.enums.BrokerDiagnosticStatusEnum;

import java.time.Instant;

/** 连接迁移记录响应。 */
public record ConnectionMigrationResponse(
        /* 执行时间。 */
        Instant executedAt,
        /* 目标 Broker 标识。 */
        String targetBrokerId,
        /* 执行状态。 */
        BrokerDiagnosticStatusEnum status,
        /* 处理路由数量。 */
        Integer processedCount,
        /* 执行耗时毫秒数。 */
        Long durationMillis,
        /* 错误摘要，可选。 */
        String errorSummary
) {
}
