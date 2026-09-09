package com.co.kc.imchat.management.audit.model.io;

/** 审计事件操作目标请求。 */
public record AuditTargetRequest(
        /* 目标类型。 */
        String type,
        /* 目标标识，可选。 */
        String id
) {
}
