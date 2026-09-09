package com.co.kc.imchat.management.audit.model.cqrs.event;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditClientContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;

import java.time.Instant;

/** 将可信来源与审计载荷合并后的审计接收事件。 */
public record AuditIngestEvent(
        /* 由认证身份或固定 Binding 注入的来源应用。 */
        String sourceApp,
        /* 全局稳定审计标识。 */
        String auditId,
        /* 审计事件类别。 */
        AuditType auditType,
        /* 来源应用定义的稳定动作码。 */
        String action,
        /* 操作者。 */
        AuditActor actor,
        /* 操作目标。 */
        AuditTarget target,
        /* 执行结果。 */
        AuditOutcome outcome,
        /* 稳定失败码，可选。 */
        String errorCode,
        /* 安全审计说明。 */
        AuditDescription description,
        /* 非安全客户端元数据，可选。 */
        AuditClientContext client,
        /* 调用链标识，可选。 */
        String traceId,
        /* 显式扩展属性。 */
        AuditAttributes attributes,
        /* 事件发生时间。 */
        Instant occurredAt
) {
    public AuditIngestEvent {
        AssertUtils.argNotBlank("audit source app must not be blank", sourceApp);
        AssertUtils.argNotBlank("audit id must not be blank", auditId);
        AssertUtils.argNotBlank("audit action must not be blank", action);
        AssertUtils.allArgNotNull(
                "audit ingest required fields must not be null",
                auditType,
                actor,
                target,
                outcome,
                description,
                attributes,
                occurredAt);
    }
}
