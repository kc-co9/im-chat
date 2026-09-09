package com.co.kc.imchat.management.audit.application;

import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.transformer.application.AuditIngestionTransformer;
import lombok.RequiredArgsConstructor;

/** 对 HTTP 与 Kafka 接收的审计事实执行统一转换和幂等追加。 */
@RequiredArgsConstructor
public class AuditIngestionAppService {
    private final AuditEventRepository auditEventRepository;
    private final AuditIngestionTransformer transformer;

    /** 追加已由接口边界注入可信来源的审计事实；重复 auditId 按幂等成功处理。 */
    public void ingest(AuditIngestEvent event) {
        AuditEvent auditEvent = transformer.auditEventFrom(event);
        auditEventRepository.append(auditEvent);
    }
}
