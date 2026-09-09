package com.co.kc.imchat.management.audit.model.cqrs.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuditIngestEventTest {

    @Test
    void exposesFlattenedAuditPropertiesWithOneTrustedSource() {
        assertThat(Arrays.stream(AuditIngestEvent.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .contains("sourceApp", "auditId", "auditType", "action", "occurredAt")
                .doesNotContain("transportSource", "event");
    }
}
