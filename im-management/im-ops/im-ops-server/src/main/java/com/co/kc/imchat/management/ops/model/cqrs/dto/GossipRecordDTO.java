package com.co.kc.imchat.management.ops.model.cqrs.dto;

import java.time.Instant;
import java.time.Duration;

/** Gossip 同步诊断记录。 */
public record GossipRecordDTO(
        Instant executedAt, String target, String status,
        Integer processedCount,
        Duration duration,
        String errorSummary
) {
}
