package com.co.kc.imchat.management.audit.model.io;

/** 审计事件操作者请求。 */
public record AuditActorRequest(
        /* 操作者类型。 */
        String type,
        /* 稳定操作者标识，可选。 */
        String id,
        /* 展示名称，可选。 */
        String name
) {
}
