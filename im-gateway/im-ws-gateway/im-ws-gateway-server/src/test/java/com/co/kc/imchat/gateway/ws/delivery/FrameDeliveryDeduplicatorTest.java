package com.co.kc.imchat.gateway.ws.delivery;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FrameDeliveryDeduplicatorTest {

    private final AtomicLong clock = new AtomicLong(1_000L);

    @Test
    void deduplicatesPerDeliveryAndConnection() {
        FrameDeliveryDeduplicator deduplicator =
                new FrameDeliveryDeduplicator(10, Duration.ofSeconds(1), clock::get);

        assertThat(deduplicator.acquire("delivery-1", "connection-a"))
                .isEqualTo(FrameDeliveryDeduplicator.Decision.ACQUIRED);
        assertThat(deduplicator.acquire("delivery-1", "connection-a"))
                .isEqualTo(FrameDeliveryDeduplicator.Decision.DUPLICATE);
        assertThat(deduplicator.acquire("delivery-1", "connection-b"))
                .isEqualTo(FrameDeliveryDeduplicator.Decision.ACQUIRED);
    }

    @Test
    void refusesCapacityWithoutEvictingKeysInsideRetentionWindow() {
        FrameDeliveryDeduplicator deduplicator =
                new FrameDeliveryDeduplicator(1, Duration.ofSeconds(1), clock::get);
        deduplicator.acquire("delivery-1", "connection-a");

        assertThat(deduplicator.acquire("delivery-2", "connection-a"))
                .isEqualTo(FrameDeliveryDeduplicator.Decision.CAPACITY_EXHAUSTED);
        assertThat(deduplicator.acquire("delivery-1", "connection-a"))
                .isEqualTo(FrameDeliveryDeduplicator.Decision.DUPLICATE);
    }

    @Test
    void failedWriteCanReleaseReservationAndExpiredKeyCanBeReused() {
        FrameDeliveryDeduplicator deduplicator =
                new FrameDeliveryDeduplicator(1, Duration.ofSeconds(1), clock::get);
        deduplicator.acquire("delivery-1", "connection-a");
        deduplicator.release("delivery-1", "connection-a");

        assertThat(deduplicator.acquire("delivery-1", "connection-a"))
                .isEqualTo(FrameDeliveryDeduplicator.Decision.ACQUIRED);
        clock.set(2_001L);
        assertThat(deduplicator.acquire("delivery-2", "connection-a"))
                .isEqualTo(FrameDeliveryDeduplicator.Decision.ACQUIRED);
    }
}
