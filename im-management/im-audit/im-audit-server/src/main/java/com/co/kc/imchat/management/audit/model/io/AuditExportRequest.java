package com.co.kc.imchat.management.audit.model.io;

import com.co.kc.imchat.management.audit.model.enums.AuditOutcomeEnum;
import com.co.kc.imchat.management.audit.model.enums.AuditTypeEnum;

/** 审计记录导出请求。 */
public record AuditExportRequest(
        /* 来源应用，可选。 */
        String sourceApp,
        /* 审计类别，可选。 */
        AuditTypeEnum type,
        /* 来源动作码，可选。 */
        String action,
        /* 执行结果，可选。 */
        AuditOutcomeEnum outcome,
        /* 操作者标识，可选。 */
        String actorId,
        /* 目标类型，可选。 */
        String targetType,
        /* 目标标识，可选。 */
        String targetId,
        /* 调用链标识，可选。 */
        String traceId,
        /* 导出起始时间。 */
        Long occurredFrom,
        /* 导出结束时间。 */
        Long occurredTo,
        /* 导出展示使用的 IANA 时区。 */
        String timeZone
) {
}
