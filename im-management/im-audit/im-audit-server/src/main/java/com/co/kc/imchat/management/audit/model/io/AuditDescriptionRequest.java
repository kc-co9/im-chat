package com.co.kc.imchat.management.audit.model.io;

/** 审计说明请求。 */
public record AuditDescriptionRequest(
        /* 已脱敏且有容量上限的说明。 */
        String value
) {
}
