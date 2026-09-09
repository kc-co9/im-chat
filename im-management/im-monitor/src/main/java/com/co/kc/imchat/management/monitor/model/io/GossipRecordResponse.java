package com.co.kc.imchat.management.monitor.model.io;

/** Gossip 诊断记录响应。 */
public record GossipRecordResponse(Long executedAt, String target, String status,
                                   Integer processedCount, Long durationMillis,
                                   String errorSummary) {
}
