package com.co.kc.imchat.management.iam.support.audit;

import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.support.AuditEventFactory;
import com.co.kc.imchat.management.audit.sdk.support.AuditFailureReporter;
import com.co.kc.imchat.management.audit.sdk.support.AuditTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamAuditPublisherTest {

    @Test
    void publishesSafeIamEvent() {
        AuditClient client = mock(AuditClient.class);
        com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector collector = mock(
                com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector.class);
        when(collector.collect()).thenReturn(new AuditContext(
                new AuditActor("SYSTEM", null, "system"), null, "trace-1"));
        AuditEventFactory factory = new AuditEventFactory(
                Clock.fixed(Instant.parse("2026-08-28T04:00:00Z"), ZoneOffset.UTC));
        AuditTemplate template = new AuditTemplate(
                client,
                collector,
                factory,
                new AuditFailureReporter(new SimpleMeterRegistry()));
        IamAuditPublisher publisher = new IamAuditPublisher(template);

        publisher.publish(
                "admin",
                "LOGIN",
                "ADMINISTRATOR_LOGIN",
                "admin",
                AuditOutcome.SUCCESS,
                null,
                "管理员登录成功");

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(client).submit(event.capture());
        assertThat(event.getValue().actor())
                .isEqualTo(new AuditActor("IAM_PRINCIPAL", null, "admin"));
        assertThat(event.getValue().attributes().values()).isEmpty();
        assertThat(event.getValue().toString())
                .doesNotContain("password", "secret", "token");
    }
}
