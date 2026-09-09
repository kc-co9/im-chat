package com.co.kc.imchat.management.audit.model.io;

import com.co.kc.imchat.management.audit.model.enums.AuditOutcomeEnum;
import com.co.kc.imchat.management.audit.model.enums.AuditTypeEnum;

import java.time.Instant;

/** 审计事件接收请求。 */
public record AuditIngestRequest(
        /* 全局稳定审计标识。 */
        String auditId,
        /* 审计事件类别。 */
        AuditTypeEnum auditType,
        /* 来源应用定义的稳定动作码。 */
        String action,
        /* 操作者。 */
        AuditActorRequest actor,
        /* 操作目标。 */
        AuditTargetRequest target,
        /* 执行结果。 */
        AuditOutcomeEnum outcome,
        /* 稳定失败码，可选。 */
        String errorCode,
        /* 安全审计说明。 */
        AuditDescriptionRequest description,
        /* 非安全客户端元数据，可选。 */
        AuditClientRequest client,
        /* 调用链标识，可选。 */
        String traceId,
        /* 显式扩展属性。 */
        AuditAttributesRequest attributes,
        /* 事件发生时间。 */
        Instant occurredAt
) {
}
