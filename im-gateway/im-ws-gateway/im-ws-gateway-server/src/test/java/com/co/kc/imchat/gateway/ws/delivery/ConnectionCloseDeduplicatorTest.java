package com.co.kc.imchat.gateway.ws.delivery;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionCloseDeduplicatorTest {

    @Test
    void deduplicatesStableOperationAndSessionTuple() {
        ConnectionCloseDeduplicator deduplicator = new ConnectionCloseDeduplicator(
                10, Duration.ofMinutes(1), System::currentTimeMillis);

        assertThat(deduplicator.acquire("operation-1", 42L, "session-old"))
                .isEqualTo(ConnectionCloseDeduplicator.Decision.ACQUIRED);
        assertThat(deduplicator.acquire("operation-1", 42L, "session-old"))
                .isEqualTo(ConnectionCloseDeduplicator.Decision.IN_PROGRESS);
        deduplicator.complete("operation-1", 42L, "session-old");
        assertThat(deduplicator.acquire("operation-1", 42L, "session-old"))
                .isEqualTo(ConnectionCloseDeduplicator.Decision.DUPLICATE);
        assertThat(deduplicator.acquire("operation-1", 42L, "session-new"))
                .isEqualTo(ConnectionCloseDeduplicator.Decision.ACQUIRED);
    }

    @Test
    void releasesFailedCloseForRetry() {
        ConnectionCloseDeduplicator deduplicator = new ConnectionCloseDeduplicator(
                1, Duration.ofMinutes(1), System::currentTimeMillis);

        assertThat(deduplicator.acquire("operation-1", 42L, "session-old"))
                .isEqualTo(ConnectionCloseDeduplicator.Decision.ACQUIRED);
        assertThat(deduplicator.acquire("operation-2", 42L, "session-old"))
                .isEqualTo(ConnectionCloseDeduplicator.Decision.CAPACITY_EXHAUSTED);
        deduplicator.release("operation-1", 42L, "session-old");
        assertThat(deduplicator.acquire("operation-1", 42L, "session-old"))
                .isEqualTo(ConnectionCloseDeduplicator.Decision.ACQUIRED);
    }
}
