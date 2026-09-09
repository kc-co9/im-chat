package com.co.kc.imchat.management.audit.model.cqrs.dto;

import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;

import java.time.Instant;

/** 审计列表项应用 DTO。 */
public record AuditListDTO(
        String auditId,
        AuditType type,
        String sourceApp,
        String action,
        String actorId,
        String actorName,
        String targetType,
        String targetId,
        AuditOutcome outcome,
        String description,
        Instant occurredAt
) {
}
