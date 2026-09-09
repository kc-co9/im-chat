package com.co.kc.imchat.management.audit.sdk.model;

import java.io.Serializable;

/** 审计事件操作者。 */
public record AuditActor(
        /* 操作者类型。 */
        String type,
        /* 稳定操作者标识，可选。 */
        String id,
        /* 展示名称，可选。 */
        String name
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AuditActor {
        AuditContract.validateRequired("audit actor type", type, AuditContract.TYPE_LENGTH);
        AuditContract.validateOptional("audit actor id", id, AuditContract.ID_LENGTH);
        AuditContract.validateOptional("audit actor name", name, AuditContract.NAME_LENGTH);
    }
}
