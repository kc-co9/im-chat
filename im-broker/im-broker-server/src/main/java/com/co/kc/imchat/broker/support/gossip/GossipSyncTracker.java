package com.co.kc.imchat.broker.support.gossip;

import com.co.kc.imchat.broker.model.cqrs.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.model.enums.DiagnosticStatus;
import com.co.kc.imchat.broker.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 跟踪 Gossip 同步诊断记录与累计摘要。
 */
public final class GossipSyncTracker {
    private static final int MAX_CAPACITY = 1_000;
    private static final int MAX_QUERY_LIMIT = 100;

    private final int capacity;
    private final Deque<GossipRecordDTO> records = new ArrayDeque<>();
    private long successCount;
    private long failureCount;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private Instant lastExecutedAt;

    public GossipSyncTracker(Integer capacity) {
        AssertUtils.argNotNull("capacity must not be null", capacity);
        AssertUtils.argTrue("capacity must be between 1 and 1000",
                capacity > 0 && capacity <= MAX_CAPACITY);
        this.capacity = capacity;
    }

    public synchronized void track(GossipRecordDTO recordDTO) {
        AssertUtils.argNotNull("record must not be null", recordDTO);
        records.addFirst(recordDTO);
        if (records.size() > capacity) {
            records.removeLast();
        }
        Instant executedAt = recordDTO.executedAt();
        lastExecutedAt = latest(lastExecutedAt, executedAt);
        if (recordDTO.status() == DiagnosticStatus.SUCCESS) {
            successCount++;
            lastSuccessAt = latest(lastSuccessAt, executedAt);
        } else {
            failureCount++;
            lastFailureAt = latest(lastFailureAt, executedAt);
        }
    }

    public synchronized List<GossipRecordDTO> recent(Integer limit) {
        AssertUtils.argNotNull("limit must not be null", limit);
        AssertUtils.argTrue("limit must be between 1 and 100",
                limit > 0 && limit <= MAX_QUERY_LIMIT);
        return records.stream().limit(limit).toList();
    }

    public synchronized DiagnosticSummaryDTO summary() {
        return new DiagnosticSummaryDTO(successCount, failureCount,
                lastSuccessAt, lastFailureAt, lastExecutedAt);
    }

    private Instant latest(Instant current, Instant candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
