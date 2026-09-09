package com.co.kc.imchat.management.audit.transformer.interfaces;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditDetailDTO;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditListDTO;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditExportQuery;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditPageQuery;
import com.co.kc.imchat.management.audit.model.enums.AuditOutcomeEnum;
import com.co.kc.imchat.management.audit.model.enums.AuditTypeEnum;
import com.co.kc.imchat.management.audit.model.io.AuditDetailResponse;
import com.co.kc.imchat.management.audit.model.io.AuditExportRequest;
import com.co.kc.imchat.management.audit.model.io.AuditIngestRequest;
import com.co.kc.imchat.management.audit.model.io.AuditListResponse;
import com.co.kc.imchat.management.audit.model.io.AuditPageRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

/** Audit 应用 DTO 到 HTTP 响应的转换器。 */
@Mapper
public interface AuditHttpTransformer {
    AuditHttpTransformer INSTANCE = Mappers.getMapper(AuditHttpTransformer.class);

    AuditListResponse auditListResponseFrom(AuditListDTO audit);

    AuditDetailResponse auditDetailResponseFrom(AuditDetailDTO audit);

    @Mapping(target = "paging", source = "paging")
    AuditPageQuery auditPageQueryFrom(AuditPageRequest request, Paging paging);

    AuditExportQuery auditExportQueryFrom(AuditExportRequest request);

    @Mapping(target = "sourceApp", source = "sourceApp")
    @Mapping(target = "auditId", source = "request.auditId")
    @Mapping(target = "auditType", source = "request.auditType")
    @Mapping(target = "action", source = "request.action")
    @Mapping(target = "actor", source = "request.actor")
    @Mapping(target = "target", source = "request.target")
    @Mapping(target = "outcome", source = "request.outcome")
    @Mapping(target = "errorCode", source = "request.errorCode")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "client", source = "request.client")
    @Mapping(target = "traceId", source = "request.traceId")
    @Mapping(target = "attributes", source = "request.attributes")
    @Mapping(target = "occurredAt", source = "request.occurredAt")
    AuditIngestEvent auditIngestEventFrom(
            AuditIngestRequest request,
            String sourceApp);

    AuditType auditTypeFrom(AuditTypeEnum type);

    AuditOutcome auditOutcomeFrom(AuditOutcomeEnum outcome);

    default Instant instantFrom(Long timestamp) {
        return timestamp == null ? null : Instant.ofEpochMilli(timestamp);
    }

    default Long timestampFrom(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
