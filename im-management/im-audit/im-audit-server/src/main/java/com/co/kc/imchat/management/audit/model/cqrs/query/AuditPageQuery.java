package com.co.kc.imchat.management.audit.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;

import java.time.Instant;

/** 审计事实分页查询。 */
public record AuditPageQuery(
        /* 分页参数。 */
        Paging paging,
        /* 来源应用，可选。 */
        String sourceApp,
        /* 审计类别，可选。 */
        AuditType type,
        /* 来源动作码，可选。 */
        String action,
        /* 执行结果，可选。 */
        AuditOutcome outcome,
        /* 操作者标识，可选。 */
        String actorId,
        /* 目标类型，可选。 */
        String targetType,
        /* 目标标识，可选。 */
        String targetId,
        /* 调用链标识，可选。 */
        String traceId,
        /* 查询起始时间，必填。 */
        Instant occurredFrom,
        /* 查询结束时间，必填。 */
        Instant occurredTo
) {
    public AuditPageQuery {
        sourceApp = normalize(sourceApp);
        action = normalize(action);
        actorId = normalize(actorId);
        targetType = normalize(targetType);
        targetId = normalize(targetId);
        traceId = normalize(traceId);
        AssertUtils.allArgNotNull(
                "audit page required values must not be null",
                paging,
                occurredFrom,
                occurredTo);
        AssertUtils.argTrue(
                "audit page time range is invalid",
                !occurredTo.isBefore(occurredFrom));
        AssertUtils.argTrue(
                "audit target type is required when target id is present",
                targetId == null || targetType != null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
