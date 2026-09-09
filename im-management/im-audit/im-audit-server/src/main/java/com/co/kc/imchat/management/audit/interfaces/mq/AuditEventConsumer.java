package com.co.kc.imchat.management.audit.interfaces.mq;

import com.co.kc.imchat.management.audit.application.AuditIngestionAppService;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import lombok.RequiredArgsConstructor;

/** 按固定 Topic 来源接收审计事实的 Kafka 适配器。 */
@RequiredArgsConstructor
public class AuditEventConsumer {
    private final AuditIngestionAppService auditIngestionAppService;

    /**
     * 接收已由 binding 边界注入可信来源的事件。
     *
     * <p>正常返回后 Binder 才确认消费；异常交由 Binder 的重试和 DLQ 策略处理。</p>
     */
    public void accept(AuditIngestEvent event) {
        auditIngestionAppService.ingest(event);
    }
}
