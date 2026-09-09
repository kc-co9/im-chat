package com.co.kc.imchat.management.audit.sdk.model;

import java.io.Serializable;

/** 已脱敏且有容量上限的审计说明。 */
public record AuditDescription(String value) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AuditDescription {
        AuditContract.validateRequired(
                "audit description",
                value,
                AuditContract.DESCRIPTION_LENGTH);
    }
}
