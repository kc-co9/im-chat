package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 已脱敏且有界的审计说明。 */
public record AuditDescription(String value) {
    public AuditDescription {
        AssertUtils.domainPropNotBlank("audit description must not be blank", value);
    }
}
