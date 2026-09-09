package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 来源应用定义的稳定审计动作。 */
public record AuditAction(String value) {
    public AuditAction {
        AssertUtils.domainPropNotBlank("audit action must not be blank", value);
    }
}
