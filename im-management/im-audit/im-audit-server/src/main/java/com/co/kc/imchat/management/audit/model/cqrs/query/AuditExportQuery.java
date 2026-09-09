package com.co.kc.imchat.management.audit.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 审计事实导出查询。 */
public record AuditExportQuery(
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
        /* 导出起始时间，必填。 */
        Instant occurredFrom,
        /* 导出结束时间，必填。 */
        Instant occurredTo,
        /* 导出展示使用的 IANA 时区。 */
        String timeZone
) {
    private static final Duration MAXIMUM_TIME_RANGE = Duration.ofDays(31);

    public AuditExportQuery {
        sourceApp = normalize(sourceApp);
        action = normalize(action);
        actorId = normalize(actorId);
        targetType = normalize(targetType);
        targetId = normalize(targetId);
        traceId = normalize(traceId);
        AssertUtils.allArgNotNull(
                "audit export time range must not be null",
                occurredFrom,
                occurredTo);
        AssertUtils.argNotBlank("audit export time zone must not be blank", timeZone);
        ZoneId.of(timeZone);
        AssertUtils.argTrue(
                "audit export time range is invalid",
                !occurredTo.isBefore(occurredFrom));
        AssertUtils.argTrue(
                "audit export time range must not exceed 31 days",
                Duration.between(occurredFrom, occurredTo).compareTo(MAXIMUM_TIME_RANGE) <= 0);
        AssertUtils.argTrue(
                "audit target type is required when target id is present",
                targetId == null || targetType != null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
