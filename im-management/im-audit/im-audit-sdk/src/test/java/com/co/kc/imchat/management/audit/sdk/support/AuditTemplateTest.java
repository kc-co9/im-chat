package com.co.kc.imchat.management.audit.sdk.support;

import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.audit.sdk.model.AuditSubmission;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditTemplateTest {

    @Test
    void submitsEventUsingCollectedContext() {
        AuditClient client = mock(AuditClient.class);
        AuditContextCollector collector = mock(AuditContextCollector.class);
        AuditContext context = new AuditContext(
                new AuditActor("SYSTEM", null, "system"), null, "trace-1");
        when(collector.collect()).thenReturn(context);
        AuditEventFactory factory = new AuditEventFactory(
                Clock.fixed(Instant.parse("2026-08-28T04:00:00Z"), ZoneOffset.UTC));
        AuditTemplate template = new AuditTemplate(
                client, collector, factory, new AuditFailureReporter(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

        template.submit(new AuditSubmission(
                AuditType.SECURITY,
                "LOGIN",
                new AuditTarget("ADMIN", "admin"),
                AuditOutcome.SUCCESS,
                null,
                new AuditDescription("登录成功"),
                new AuditAttributes(Map.of())));

        org.mockito.ArgumentCaptor<AuditEvent> event =
                org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(client).submit(event.capture());
        assertThat(event.getValue().actor()).isEqualTo(context.actor());
        assertThat(event.getValue().traceId()).isEqualTo("trace-1");
    }
}
