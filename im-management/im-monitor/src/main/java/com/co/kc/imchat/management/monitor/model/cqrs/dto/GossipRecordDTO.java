package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import java.time.Instant;

/** Gossip 同步诊断记录。 */
public record GossipRecordDTO(
        Instant executedAt, String target, String status,
        Integer processedCount,
        Long durationMillis,
        String errorSummary
) {
}
