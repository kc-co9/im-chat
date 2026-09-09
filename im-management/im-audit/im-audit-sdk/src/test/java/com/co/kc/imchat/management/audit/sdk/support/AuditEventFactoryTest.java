package com.co.kc.imchat.management.audit.sdk.support;

import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditClientContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventFactoryTest {

    @Test
    void generatesIdentityAndOccurrenceTimeOnce() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T04:00:00Z"),
                ZoneOffset.UTC);
        AuditEventFactory factory = new AuditEventFactory(clock);
        AuditContext context = new AuditContext(
                new AuditActor("ADMINISTRATOR", "1001", "admin"),
                new AuditClientContext("127.0.0.1", "JUnit"),
                "trace-1");

        AuditEvent event = factory.create(
                AuditType.BUSINESS,
                "USER_BAN",
                new AuditTarget("USER", "2001"),
                AuditOutcome.SUCCESS,
                null,
                new AuditDescription("封禁普通用户"),
                new AuditAttributes(Map.of()),
                context);

        assertThat(event.auditId()).isNotBlank();
        assertThat(event.occurredAt()).isEqualTo(clock.instant());
    }
}
