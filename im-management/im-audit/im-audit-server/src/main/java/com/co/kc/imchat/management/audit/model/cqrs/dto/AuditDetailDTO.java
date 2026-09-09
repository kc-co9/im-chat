package com.co.kc.imchat.management.audit.model.cqrs.dto;

import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;

import java.time.Instant;
import java.util.Map;

/** 审计详情应用 DTO。 */
public record AuditDetailDTO(
        String auditId,
        AuditType type,
        String sourceApp,
        String action,
        String actorType,
        String actorId,
        String actorName,
        String targetType,
        String targetId,
        AuditOutcome outcome,
        String errorCode,
        String description,
        String clientAddress,
        String userAgent,
        String traceId,
        Map<String, String> attributes,
        Instant occurredAt
) {
    public AuditDetailDTO {
        attributes = Map.copyOf(attributes);
    }
}
