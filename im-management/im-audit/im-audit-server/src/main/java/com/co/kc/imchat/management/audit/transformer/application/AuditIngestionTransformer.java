package com.co.kc.imchat.management.audit.transformer.application;

import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditErrorCode;
import com.co.kc.imchat.management.audit.domain.model.TraceId;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** 可信传输来源、SDK 事件与 Audit 领域聚合的转换器。 */
@Mapper
public interface AuditIngestionTransformer {
    AuditIngestionTransformer INSTANCE = Mappers.getMapper(AuditIngestionTransformer.class);

    @Mapping(target = "sourceApp", source = "sourceApp")
    @Mapping(target = "auditId", source = "event.auditId")
    @Mapping(target = "auditType", source = "event.auditType")
    @Mapping(target = "action", source = "event.action")
    @Mapping(target = "actor", source = "event.actor")
    @Mapping(target = "target", source = "event.target")
    @Mapping(target = "outcome", source = "event.outcome")
    @Mapping(target = "errorCode", source = "event.errorCode")
    @Mapping(target = "description", source = "event.description")
    @Mapping(target = "client", source = "event.client")
    @Mapping(target = "traceId", source = "event.traceId")
    @Mapping(target = "attributes", source = "event.attributes")
    @Mapping(target = "occurredAt", source = "event.occurredAt")
    AuditIngestEvent auditIngestEventFrom(
            String sourceApp,
            com.co.kc.imchat.management.audit.sdk.model.AuditEvent event);

    @Mapping(target = "id.value", source = "auditId")
    @Mapping(target = "sourceApp.value", source = "sourceApp")
    @Mapping(target = "type", source = "auditType")
    @Mapping(target = "action.value", source = "action")
    @Mapping(target = "errorCode", expression = "java(auditErrorCodeFrom(event.errorCode()))")
    @Mapping(target = "description.value", source = "description.value")
    @Mapping(target = "clientContext", source = "client")
    @Mapping(target = "traceId", expression = "java(traceIdFrom(event.traceId()))")
    @Mapping(target = "attributes.values", source = "attributes.values")
    AuditEvent auditEventFrom(AuditIngestEvent event);

    default AuditErrorCode auditErrorCodeFrom(String errorCode) {
        return errorCode == null ? null : new AuditErrorCode(errorCode);
    }

    default TraceId traceIdFrom(String traceId) {
        return traceId == null ? null : new TraceId(traceId);
    }
}
