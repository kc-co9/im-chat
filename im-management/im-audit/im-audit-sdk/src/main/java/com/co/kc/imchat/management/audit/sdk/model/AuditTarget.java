package com.co.kc.imchat.management.audit.sdk.model;

import java.io.Serializable;

/** 审计事件操作目标。 */
public record AuditTarget(
        /* 目标类型。 */
        String type,
        /* 目标标识，可选。 */
        String id
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AuditTarget {
        AuditContract.validateRequired("audit target type", type, AuditContract.TYPE_LENGTH);
        AuditContract.validateOptional("audit target id", id, AuditContract.ID_LENGTH);
    }
}
