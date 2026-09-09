package com.co.kc.imchat.management.audit.model.io;

import com.co.kc.imchat.management.audit.model.enums.AuditOutcomeEnum;
import com.co.kc.imchat.management.audit.model.enums.AuditTypeEnum;

/** 审计列表响应项。 */
public record AuditListResponse(
        /* 审计业务标识。 */
        String auditId,
        /* 审计类别。 */
        AuditTypeEnum type,
        /* 来源应用。 */
        String sourceApp,
        /* 来源动作码。 */
        String action,
        /* 操作者标识。 */
        String actorId,
        /* 操作者名称快照。 */
        String actorName,
        /* 目标类型。 */
        String targetType,
        /* 目标标识。 */
        String targetId,
        /* 执行结果。 */
        AuditOutcomeEnum outcome,
        /* 安全审计说明。 */
        String description,
        /* 来源事件发生时间。 */
        Long occurredAt
) {
}
