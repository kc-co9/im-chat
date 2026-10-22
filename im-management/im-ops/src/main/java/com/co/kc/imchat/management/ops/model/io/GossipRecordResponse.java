package com.co.kc.imchat.management.ops.model.io;

/** Gossip 诊断记录响应。 */
public record GossipRecordResponse(Long executedAtMs, String target, String status,
                                   Integer processedCount, Long durationMs,
                                   String errorSummary) {
}
