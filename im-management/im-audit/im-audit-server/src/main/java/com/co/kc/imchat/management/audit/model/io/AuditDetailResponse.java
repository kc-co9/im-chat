package com.co.kc.imchat.management.audit.model.io;

import com.co.kc.imchat.management.audit.model.enums.AuditOutcomeEnum;
import com.co.kc.imchat.management.audit.model.enums.AuditTypeEnum;

import java.util.Map;

/** 审计详情响应。 */
public record AuditDetailResponse(
        /* 审计业务标识。 */
        String auditId,
        /* 审计类别。 */
        AuditTypeEnum type,
        /* 来源应用。 */
        String sourceApp,
        /* 来源动作码。 */
        String action,
        /* 操作者类型。 */
        String actorType,
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
        /* 稳定失败码。 */
        String errorCode,
        /* 安全审计说明。 */
        String description,
        /* 客户端地址。 */
        String clientAddress,
        /* 客户端标识。 */
        String userAgent,
        /* 调用链标识。 */
        String traceId,
        /* 显式扩展属性。 */
        Map<String, String> attributes,
        /* 来源事件发生时间。 */
        Long occurredAt
) {
    public AuditDetailResponse {
        attributes = Map.copyOf(attributes);
    }
}
