package com.co.kc.imchat.management.audit.interfaces.mq;

import com.co.kc.imchat.management.audit.application.AuditIngestionAppService;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.audit.transformer.application.AuditIngestionTransformer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditEventConsumerTest {

    @Test
    void derivesSourceFromBindingInsteadOfMessageBody() {
        AuditIngestionAppService appService = mock(AuditIngestionAppService.class);
        AuditEventConsumer consumer = new AuditEventConsumer(appService);
        AuditEvent event = event();

        AuditIngestEvent ingestEvent = AuditIngestionTransformer.INSTANCE.auditIngestEventFrom(
                "imAdmin",
                event);
        consumer.accept(ingestEvent);

        verify(appService).ingest(ingestEvent);
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
                null,
                new AuditAttributes(Map.of()),
                Instant.parse("2026-08-28T04:00:00Z"));
    }
}
