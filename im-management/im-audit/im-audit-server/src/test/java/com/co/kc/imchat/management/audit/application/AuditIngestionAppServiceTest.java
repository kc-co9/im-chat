package com.co.kc.imchat.management.audit.application;

import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.audit.transformer.application.AuditIngestionTransformer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditIngestionAppServiceTest {

    @Test
    void persistsTransportEventAndTreatsDuplicateAsSuccess() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditIngestionTransformer transformer = mock(AuditIngestionTransformer.class);
        AuditEvent domainEvent = mock(AuditEvent.class);
        AuditIngestEvent ingestEvent = AuditIngestionTransformer.INSTANCE.auditIngestEventFrom(
                "imAdmin",
                event());
        when(transformer.auditEventFrom(ingestEvent)).thenReturn(domainEvent);
        when(repository.append(domainEvent)).thenReturn(true, false);
        AuditIngestionAppService service = new AuditIngestionAppService(repository, transformer);

        assertThatCode(() -> {
            service.ingest(ingestEvent);
            service.ingest(ingestEvent);
        }).doesNotThrowAnyException();

        verify(repository, org.mockito.Mockito.times(2)).append(domainEvent);
    }

    private com.co.kc.imchat.management.audit.sdk.model.AuditEvent event() {
        return new com.co.kc.imchat.management.audit.sdk.model.AuditEvent(
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
