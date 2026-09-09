package com.co.kc.imchat.management.audit.sdk.transport.kafka;

import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.properties.AuditKafkaProperties;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransport;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransportException;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

/** 通过 Spring Cloud Stream Kafka Binder 投递审计事件。 */
@RequiredArgsConstructor
public class KafkaAuditTransport implements AuditTransport {
    /* Kafka Binder 识别的消息 Key Header；避免 SDK 引入 Kafka 原生客户端。 */
    private static final String KAFKA_MESSAGE_KEY = "kafka_messageKey";

    private final StreamBridge streamBridge;
    private final AuditKafkaProperties properties;

    @Override
    public void send(AuditEvent event) {
        Message<AuditEvent> message = MessageBuilder.withPayload(event)
                .setHeader(
                        KAFKA_MESSAGE_KEY,
                        event.auditId().getBytes(StandardCharsets.UTF_8))
                .build();
        if (!streamBridge.send(properties.bindingName(), message)) {
            throw new AuditTransportException("Kafka Binder rejected audit event");
        }
    }
}
