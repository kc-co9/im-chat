package com.co.kc.imchat.management.ops.infrastructure.client.model;
/** Gossip 诊断协议载荷。 */
public record GossipRecordPayload(Long executedAtMs, String target, String status,
                                  Integer processedCount, Long durationMs, String errorSummary) { }
