package com.co.kc.imchat.management.monitor.infrastructure.client.model;
import java.time.Instant;
/** 迁移诊断协议载荷。 */
public record MigrationRecordPayload(Instant executedAt, String targetBrokerId, String status,
                                     Integer processedCount, Long durationMillis, String errorSummary) { }
