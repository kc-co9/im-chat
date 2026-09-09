package com.co.kc.imchat.management.monitor.model.io;

/** 连接迁移诊断记录响应。 */
public record MigrationRecordResponse(Long executedAt, String targetBrokerId, String status,
                                      Integer processedCount, Long durationMillis,
                                      String errorSummary) {
}
