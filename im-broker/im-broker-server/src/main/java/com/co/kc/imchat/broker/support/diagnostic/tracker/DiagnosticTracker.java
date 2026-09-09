package com.co.kc.imchat.broker.support.diagnostic.tracker;

import com.co.kc.imchat.broker.support.diagnostic.model.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.enums.DiagnosticStatus;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * 诊断记录跟踪能力。
 *
 * @param <T> 诊断记录类型
 */
public interface DiagnosticTracker<T> {

    void track(T record);

    List<T> recent(Integer limit);

    DiagnosticSummaryDTO summary();

    /**
     * Tracker 共用的有界记录和累计摘要状态。
     *
     * @param <T> 诊断记录类型
     */
    final class State<T> {
        private static final int MAX_CAPACITY = 1_000;
        private static final int MAX_QUERY_LIMIT = 100;

        private final int capacity;
        private final Function<T, DiagnosticStatus> statusExtractor;
        private final Function<T, Instant> timeExtractor;
        private final Deque<T> records = new ArrayDeque<>();
        private long successCount;
        private long failureCount;
        private Instant lastSuccessAt;
        private Instant lastFailureAt;
        private Instant lastExecutedAt;

        public State(
                Integer capacity,
                Function<T, DiagnosticStatus> statusExtractor,
                Function<T, Instant> timeExtractor
        ) {
            AssertUtils.allArgNotNull(
                    "diagnostic tracker properties must not be null",
                    capacity,
                    statusExtractor,
                    timeExtractor);
            AssertUtils.argTrue(
                    "capacity must be between 1 and 1000",
                    capacity > 0 && capacity <= MAX_CAPACITY);
            this.capacity = capacity;
            this.statusExtractor = statusExtractor;
            this.timeExtractor = timeExtractor;
        }

        public synchronized void track(T record) {
            AssertUtils.argNotNull("record must not be null", record);
            records.addFirst(record);
            if (records.size() > capacity) {
                records.removeLast();
            }
            Instant executedAt = timeExtractor.apply(record);
            lastExecutedAt = latest(lastExecutedAt, executedAt);
            if (statusExtractor.apply(record) == DiagnosticStatus.SUCCESS) {
                successCount++;
                lastSuccessAt = latest(lastSuccessAt, executedAt);
            } else {
                failureCount++;
                lastFailureAt = latest(lastFailureAt, executedAt);
            }
        }

        public synchronized List<T> recent(Integer limit) {
            AssertUtils.argNotNull("limit must not be null", limit);
            AssertUtils.argTrue(
                    "limit must be between 1 and 100",
                    limit > 0 && limit <= MAX_QUERY_LIMIT);
            return records.stream().limit(limit).toList();
        }

        public synchronized DiagnosticSummaryDTO summary() {
            return new DiagnosticSummaryDTO(
                    successCount,
                    failureCount,
                    lastSuccessAt,
                    lastFailureAt,
                    lastExecutedAt);
        }

        private Instant latest(Instant current, Instant candidate) {
            return current == null || candidate.isAfter(current) ? candidate : current;
        }
    }
}
