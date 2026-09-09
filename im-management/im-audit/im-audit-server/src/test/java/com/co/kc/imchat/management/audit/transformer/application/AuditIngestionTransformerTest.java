package com.co.kc.imchat.management.audit.transformer.application;

import com.co.kc.imchat.management.audit.domain.model.SourceApp;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditIngestionTransformerTest {

    @Test
    void injectsTrustedSourceIntoIngestAndDomainEvents() {
        AuditEvent transportEvent = event();

        AuditIngestEvent ingestEvent = AuditIngestionTransformer.INSTANCE.auditIngestEventFrom(
                "imAdmin",
                transportEvent);
        com.co.kc.imchat.management.audit.domain.model.AuditEvent domainEvent =
                AuditIngestionTransformer.INSTANCE.auditEventFrom(ingestEvent);

        assertThat(ingestEvent.sourceApp()).isEqualTo("imAdmin");
        assertThat(ingestEvent.auditId()).isEqualTo(transportEvent.auditId());
        assertThat(domainEvent.getSourceApp()).isEqualTo(new SourceApp("imAdmin"));
        assertThat(domainEvent.getId().value()).isEqualTo(transportEvent.auditId());
    }

    private AuditEvent event() {
        return new AuditEvent(
                "audit-1",
                AuditType.BUSINESS,
                "USER_BAN",
                new AuditActor("ADMINISTRATOR", "1001", "admin"),
                new AuditTarget("USER", "2001"),
                AuditOutcome.SUCCESS,
                null,
                new AuditDescription("封禁普通用户"),
                null,
                "trace-1",
                new AuditAttributes(Map.of()),
                Instant.parse("2026-08-28T04:00:00Z"));
    }
}
