package com.co.kc.imchat.broker.support.diagnostic.tracker.impl;

import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionMigrationRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.enums.DiagnosticStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionMigrationTrackerTest {

    @Test
    void keepsBoundedMigrationHistoryAndCumulativeSummary() {
        ConnectionMigrationTracker tracker = new ConnectionMigrationTracker(1);
        tracker.track(new ConnectionMigrationRecordDTO(
                Instant.parse("2026-08-23T01:00:00Z"), "broker-1", DiagnosticStatus.SUCCESS, 2, 10L, null));
        tracker.track(new ConnectionMigrationRecordDTO(
                Instant.parse("2026-08-23T01:01:00Z"), "broker-2", DiagnosticStatus.FAILED, 3, 20L, "unavailable"));

        assertThat(tracker.recent(1)).extracting(ConnectionMigrationRecordDTO::targetBrokerId)
                .containsExactly("broker-2");
        assertThat(tracker.summary().successCount()).isEqualTo(1);
        assertThat(tracker.summary().failureCount()).isEqualTo(1);
        assertThat(tracker.summary().lastExecutedAt()).isEqualTo(Instant.parse("2026-08-23T01:01:00Z"));
    }
}
