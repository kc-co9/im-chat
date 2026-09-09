package com.co.kc.imchat.management.audit.sdk.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;
import java.time.Instant;

/** 跨进程传输的完整审计事实。 */
public record AuditEvent(
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
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AuditEvent {
        AuditContract.validateRequired("audit id", auditId, AuditContract.ID_LENGTH);
        AuditContract.validateRequired("audit action", action, AuditContract.ACTION_LENGTH);
        AuditContract.validateOptional(
                "audit error code",
                errorCode,
                AuditContract.ERROR_CODE_LENGTH);
        AuditContract.validateOptional(
                "audit trace id",
                traceId,
                AuditContract.TRACE_ID_LENGTH);
        AssertUtils.allArgNotNull(
                "audit event required fields must not be null",
                auditType,
                actor,
                target,
                outcome,
                description,
                attributes,
                occurredAt);
        AssertUtils.argTrue(
                "successful audit event must not contain an error code",
                outcome != AuditOutcome.SUCCESS || errorCode == null);
    }
}
