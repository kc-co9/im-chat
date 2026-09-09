package com.co.kc.imchat.management.audit.transformer.application;

import com.co.kc.imchat.common.domain.time.model.TimeRange;
import com.co.kc.imchat.management.audit.domain.model.AuditAction;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditQueryCondition;
import com.co.kc.imchat.management.audit.domain.model.AuditTarget;
import com.co.kc.imchat.management.audit.domain.model.SourceApp;
import com.co.kc.imchat.management.audit.domain.model.TraceId;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditDetailDTO;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditListDTO;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditExportQuery;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditPageQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Optional;

/** Audit 领域聚合到应用 DTO 的转换器。 */
@Mapper
public interface AuditAppTransformer {
    AuditAppTransformer INSTANCE = Mappers.getMapper(AuditAppTransformer.class);

    @Mapping(target = "auditId", source = "id.value")
    @Mapping(target = "sourceApp", source = "sourceApp.value")
    @Mapping(target = "action", source = "action.value")
    @Mapping(target = "actorId", source = "actor.id")
    @Mapping(target = "actorName", source = "actor.name")
    @Mapping(target = "targetType", source = "target.type")
    @Mapping(target = "targetId", source = "target.id")
    @Mapping(target = "description", source = "description.value")
    AuditListDTO auditListDtoFrom(AuditEvent event);

    @Mapping(target = "auditId", source = "id.value")
    @Mapping(target = "sourceApp", source = "sourceApp.value")
    @Mapping(target = "action", source = "action.value")
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
    AuditDetailDTO auditDetailDtoFrom(AuditEvent event);

    @Mapping(target = "sourceApp", source = "sourceApp", qualifiedByName = "sourceAppFrom")
    @Mapping(target = "type", source = "type", qualifiedByName = "optionalFrom")
    @Mapping(target = "action", source = "action", qualifiedByName = "auditActionFrom")
    @Mapping(target = "outcome", source = "outcome", qualifiedByName = "optionalFrom")
    @Mapping(target = "actorId", source = "actorId", qualifiedByName = "optionalFrom")
    @Mapping(target = "target", expression = "java(auditTargetFrom(query.targetType(), query.targetId()))")
    @Mapping(target = "traceId", source = "traceId", qualifiedByName = "traceIdFrom")
    @Mapping(target = "timeRange", expression = "java(timeRangeFrom(query.occurredFrom(), query.occurredTo()))")
    AuditQueryCondition auditQueryConditionFrom(AuditPageQuery query);

    @Mapping(target = "sourceApp", source = "sourceApp", qualifiedByName = "sourceAppFrom")
    @Mapping(target = "type", source = "type", qualifiedByName = "optionalFrom")
    @Mapping(target = "action", source = "action", qualifiedByName = "auditActionFrom")
    @Mapping(target = "outcome", source = "outcome", qualifiedByName = "optionalFrom")
    @Mapping(target = "actorId", source = "actorId", qualifiedByName = "optionalFrom")
    @Mapping(target = "target", expression = "java(auditTargetFrom(query.targetType(), query.targetId()))")
    @Mapping(target = "traceId", source = "traceId", qualifiedByName = "traceIdFrom")
    @Mapping(target = "timeRange", expression = "java(timeRangeFrom(query.occurredFrom(), query.occurredTo()))")
    AuditQueryCondition auditQueryConditionFrom(AuditExportQuery query);

    @Named("sourceAppFrom")
    default Optional<SourceApp> sourceAppFrom(String value) {
        return Optional.ofNullable(value).map(SourceApp::new);
    }

    @Named("auditActionFrom")
    default Optional<AuditAction> auditActionFrom(String value) {
        return Optional.ofNullable(value).map(AuditAction::new);
    }

    @Named("traceIdFrom")
    default Optional<TraceId> traceIdFrom(String value) {
        return Optional.ofNullable(value).map(TraceId::new);
    }

    @Named("optionalFrom")
    default <T> Optional<T> optionalFrom(T value) {
        return Optional.ofNullable(value);
    }

    default Optional<AuditTarget> auditTargetFrom(String targetType, String targetId) {
        return Optional.ofNullable(targetType)
                .map(value -> new AuditTarget(value, targetId));
    }

    default TimeRange timeRangeFrom(Instant start, Instant end) {
        return new TimeRange(start, end);
    }
}
