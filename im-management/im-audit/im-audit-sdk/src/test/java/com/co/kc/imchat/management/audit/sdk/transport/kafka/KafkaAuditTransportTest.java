package com.co.kc.imchat.management.audit.sdk.transport.kafka;

import com.co.kc.imchat.management.audit.sdk.transport.AuditTransportException;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditClientContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.audit.sdk.properties.AuditKafkaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaAuditTransportTest {

    @Test
    void sendsEventWithAuditIdentityAsKafkaKey() {
        StreamBridge streamBridge = mock(StreamBridge.class);
        KafkaAuditTransport transport = new KafkaAuditTransport(
                streamBridge,
                new AuditKafkaProperties("auditOutput"));
        AuditEvent event = event();
        when(streamBridge.send(eq("auditOutput"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        transport.send(event);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Message<AuditEvent>> messageCaptor =
                org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("auditOutput"), messageCaptor.capture());
        Message<AuditEvent> message = messageCaptor.getValue();
        assertThat(message.getPayload()).isSameAs(event);
        assertThat(message.getHeaders().get("kafka_messageKey"))
                .isEqualTo("audit-1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void rejectsBinderSendFailure() {
        StreamBridge streamBridge = mock(StreamBridge.class);
        KafkaAuditTransport transport = new KafkaAuditTransport(
                streamBridge,
                new AuditKafkaProperties("auditOutput"));
        when(streamBridge.send(eq("auditOutput"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        assertThatThrownBy(() -> transport.send(event()))
                .isInstanceOf(AuditTransportException.class)
                .hasMessageContaining("Kafka");
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
                new AuditClientContext("127.0.0.1", "JUnit"),
                "trace-1",
                new AuditAttributes(Map.of()),
                Instant.parse("2026-08-28T04:00:00Z"));
    }
}
