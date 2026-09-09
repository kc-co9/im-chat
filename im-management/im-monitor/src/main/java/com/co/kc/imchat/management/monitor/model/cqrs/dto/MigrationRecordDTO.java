package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import java.time.Instant;

/** 连接迁移诊断记录。 */
public record MigrationRecordDTO(
        Instant executedAt, String targetBrokerId, String status,
        Integer processedCount,
        Long durationMillis,
        String errorSummary
) {
}
