package com.co.kc.imchat.broker.support.diagnostic.model.dto;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.broker.support.diagnostic.model.enums.DiagnosticStatus;

import java.time.Instant;

/**
 * 单个目标 Broker 的连接迁移诊断记录。
 *
 * @param executedAt 执行时间
 * @param targetBrokerId 目标 Broker ID
 * @param status 执行状态
 * @param processedCount 迁移路由数
 * @param durationMillis 耗时毫秒数
 * @param errorSummary 截断后的错误摘要
 */
public record ConnectionMigrationRecordDTO(
        Instant executedAt,
        String targetBrokerId,
        DiagnosticStatus status,
        Integer processedCount,
        Long durationMillis,
        String errorSummary
) {
    public ConnectionMigrationRecordDTO {
        AssertUtils.argNotNull("executedAt must not be null", executedAt);
        AssertUtils.argNotBlank("targetBrokerId must not be blank", targetBrokerId);
        AssertUtils.allArgNotNull(
                "diagnostic numeric and status properties must not be null",
                status,
                processedCount,
                durationMillis);
        AssertUtils.argTrue("processedCount must not be negative", processedCount >= 0);
        AssertUtils.argTrue("durationMillis must not be negative", durationMillis >= 0);
        if (errorSummary != null && errorSummary.length() > 256) {
            errorSummary = errorSummary.substring(0, 256);
        }
    }
}
