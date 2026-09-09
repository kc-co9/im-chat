package com.co.kc.imchat.management.audit.sdk.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventTest {

    @Test
    void doesNotAllowPayloadToDeclareSourceApplication() {
        assertThat(Arrays.stream(AuditEvent.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("sourceApp");
    }

    @Test
    void copiesAttributesAndKeepsCompleteEventIdentity() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("roleCode", "AUDITOR");

        AuditEvent event = event(new AuditAttributes(values));
        values.put("scope", "audit:read");

        assertThat(event.auditId()).isEqualTo("audit-1");
        assertThat(event.attributes().values())
                .containsExactly(Map.entry("roleCode", "AUDITOR"));
        assertThatThrownBy(() -> event.attributes().values().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAttributeAndDescriptionLimits() {
        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int index = 0; index < 21; index++) {
            tooMany.put("key" + index, "value");
        }

        assertThatThrownBy(() -> new AuditAttributes(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 20");
        assertThatThrownBy(() -> event(
                new AuditAttributes(Map.of("key", "x".repeat(513)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attribute value");
        assertThatThrownBy(() -> new AuditDescription("x".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    private AuditEvent event(AuditAttributes attributes) {
        return new AuditEvent(
                "audit-1",
                AuditType.BUSINESS,
                "USER_BAN",
                new AuditActor("ADMINISTRATOR", "1001", "admin"),
                new AuditTarget("USER", "2001"),
                AuditOutcome.SUCCESS,
                null,
                new AuditDescription("封禁普通用户"),
                new AuditClientContext("127.0.0.1", "JUnit"),
                "trace-1",
                attributes,
                Instant.parse("2026-08-28T04:00:00Z"));
    }
}
