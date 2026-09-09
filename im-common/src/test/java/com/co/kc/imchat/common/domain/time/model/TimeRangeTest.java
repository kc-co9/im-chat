package com.co.kc.imchat.common.domain.time.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeRangeTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void exposesInclusiveBoundsAndDuration() {
        TimeRange range = new TimeRange(START, END);

        assertThat(range.start()).isEqualTo(START);
        assertThat(range.end()).isEqualTo(END);
        assertThat(range.duration()).isEqualTo(Duration.ofDays(1));
        assertThat(new TimeRange(START, START).duration()).isZero();
    }

    @Test
    void rejectsMissingOrReversedBounds() {
        assertThatThrownBy(() -> new TimeRange(null, END))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TimeRange(START, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TimeRange(END, START))
                .isInstanceOf(IllegalStateException.class);
    }
}
