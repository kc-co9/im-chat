package com.co.kc.imchat.broker.support.diagnostic.tracker.impl;

import com.co.kc.imchat.broker.support.diagnostic.model.enums.DiagnosticStatus;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GossipRecordDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GossipSyncTrackerTest {

    @Test
    void evictsOldestAndReturnsNewestRecordsFirst() {
        GossipSyncTracker tracker = new GossipSyncTracker(2);
        tracker.track(record(1, DiagnosticStatus.SUCCESS, null));
        tracker.track(record(2, DiagnosticStatus.FAILED, "failed"));
        tracker.track(record(3, DiagnosticStatus.SUCCESS, null));

        assertThat(tracker.recent(2)).extracting(GossipRecordDTO::target)
                .containsExactly("broker-3", "broker-2");
        assertThat(tracker.summary().successCount()).isEqualTo(2);
        assertThat(tracker.summary().failureCount()).isEqualTo(1);
        assertThat(tracker.summary().lastSuccessAt()).isEqualTo(Instant.ofEpochSecond(3));
        assertThat(tracker.summary().lastFailureAt()).isEqualTo(Instant.ofEpochSecond(2));
    }

    @Test
    void enforcesQueryLimitAndTruncatesErrorSummary() {
        GossipSyncTracker tracker = new GossipSyncTracker(200);
        tracker.track(record(1, DiagnosticStatus.FAILED, "x".repeat(500)));

        assertThat(tracker.recent(1).getFirst().errorSummary()).hasSize(256);
        assertThatThrownBy(() -> tracker.recent(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tracker.recent(101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsConcurrentWritesWithoutLosingCumulativeCounts() {
        GossipSyncTracker tracker = new GossipSyncTracker(100);

        IntStream.range(0, 500).parallel().forEach(index -> tracker.track(
                record(index, index % 2 == 0 ? DiagnosticStatus.SUCCESS : DiagnosticStatus.FAILED, null)));

        assertThat(tracker.recent(100)).hasSize(100);
        assertThat(tracker.summary().successCount()).isEqualTo(250);
        assertThat(tracker.summary().failureCount()).isEqualTo(250);
    }

    private GossipRecordDTO record(int sequence, DiagnosticStatus status, String errorSummary) {
        return new GossipRecordDTO(
                Instant.ofEpochSecond(sequence),
                "broker-" + sequence,
                status,
                sequence,
                sequence * 10L,
                errorSummary);
    }
}
