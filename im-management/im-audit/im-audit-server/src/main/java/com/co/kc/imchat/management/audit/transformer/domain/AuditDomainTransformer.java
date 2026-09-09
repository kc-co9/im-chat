package com.co.kc.imchat.management.audit.transformer.domain;

import com.co.kc.imchat.management.audit.domain.model.AuditClientContext;
import com.co.kc.imchat.management.audit.domain.model.AuditErrorCode;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.domain.model.TraceId;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.entity.DbAuditEvent;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.enums.DbAuditOutcome;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.enums.DbAuditType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;


/** 审计聚合与持久化实体转换器。 */
@Mapper
public interface AuditDomainTransformer {
    AuditDomainTransformer INSTANCE = Mappers.getMapper(AuditDomainTransformer.class);

    @Mapping(target = "id.value", source = "auditId")
    @Mapping(target = "sourceApp.value", source = "sourceApp")
    @Mapping(target = "action.value", source = "actionCode")
    @Mapping(target = "actor.type", source = "actorType")
    @Mapping(target = "actor.id", source = "actorId")
    @Mapping(target = "actor.name", source = "actorName")
    @Mapping(target = "target.type", source = "targetType")
    @Mapping(target = "target.id", source = "targetId")
    @Mapping(target = "errorCode", source = "errorCode")
    @Mapping(target = "description.value", source = "description")
    @Mapping(target = "clientContext", expression = "java(clientFrom(event))")
    @Mapping(target = "traceId", source = "traceId")
    @Mapping(target = "attributes.values", source = "attributes")
    AuditEvent auditEventFrom(DbAuditEvent event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditId", source = "id.value")
    @Mapping(target = "sourceApp", source = "sourceApp.value")
    @Mapping(target = "actionCode", source = "action.value")
    @Mapping(target = "actorType", source = "actor.type")
    @Mapping(target = "actorId", source = "actor.id")
    @Mapping(target = "actorName", source = "actor.name")
    @Mapping(target = "targetType", source = "target.type")
    @Mapping(target = "targetId", source = "target.id")
    @Mapping(target = "errorCode", source = "errorCode.value")
    @Mapping(target = "description", source = "description.value")
    @Mapping(target = "clientAddress", source = "clientContext.address")
    @Mapping(target = "userAgent", source = "clientContext.userAgent")
    @Mapping(target = "traceId", source = "traceId.value")
    @Mapping(target = "attributes", source = "attributes.values")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbAuditEvent dbAuditEventFrom(AuditEvent event);

    DbAuditType dbAuditTypeFrom(AuditType type);

    DbAuditOutcome dbAuditOutcomeFrom(AuditOutcome outcome);

    default AuditClientContext clientFrom(DbAuditEvent event) {
        if (event.getClientAddress() == null && event.getUserAgent() == null) {
            return null;
        }
        return new AuditClientContext(event.getClientAddress(), event.getUserAgent());
    }

    default AuditErrorCode auditErrorCodeFrom(String errorCode) {
        return errorCode == null ? null : new AuditErrorCode(errorCode);
    }

    default TraceId traceIdFrom(String traceId) {
        return traceId == null ? null : new TraceId(traceId);
    }

}
