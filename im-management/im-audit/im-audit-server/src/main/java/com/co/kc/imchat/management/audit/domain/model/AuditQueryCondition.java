package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.domain.time.model.TimeRange;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Optional;

/** 审计事实的有界查询条件。 */
public record AuditQueryCondition(
        Optional<SourceApp> sourceApp,
        Optional<AuditType> type,
        Optional<AuditAction> action,
        Optional<AuditOutcome> outcome,
        Optional<String> actorId,
        Optional<AuditTarget> target,
        Optional<TraceId> traceId,
        TimeRange timeRange
) {
    public AuditQueryCondition {
        AssertUtils.allDomainPropNotNull(
                "audit query optional conditions must not be null",
                sourceApp,
                type,
                action,
                outcome,
                actorId,
                target,
                traceId);
        AssertUtils.domainPropNotNull(
                "audit query time range must not be null",
                timeRange);
    }
}
