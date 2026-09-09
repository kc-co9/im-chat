package com.co.kc.imchat.management.monitor.infrastructure.client.model;
import java.time.Instant;
/** Gossip 诊断协议载荷。 */
public record GossipRecordPayload(Instant executedAt, String target, String status,
                                  Integer processedCount, Long durationMillis, String errorSummary) { }
