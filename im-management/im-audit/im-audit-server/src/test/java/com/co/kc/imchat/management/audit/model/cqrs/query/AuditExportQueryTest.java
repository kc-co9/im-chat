package com.co.kc.imchat.management.audit.model.cqrs.query;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditExportQueryTest {

    @Test
    void acceptsAtMostThirtyOneDays() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");

        assertThatCode(() -> query(from, from.plusSeconds(31L * 24 * 3600)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> query(from, from.plusSeconds(31L * 24 * 3600 + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("31 days");
    }

    @Test
    void rejectsReversedRange() {
        Instant from = Instant.parse("2026-08-02T00:00:00Z");

        assertThatThrownBy(() -> query(from, from.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AuditExportQuery query(Instant from, Instant to) {
        return new AuditExportQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                from,
                to,
                "Asia/Shanghai");
    }
}
