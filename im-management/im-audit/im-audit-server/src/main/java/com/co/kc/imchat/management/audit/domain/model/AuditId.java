package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 审计事实的全局业务标识。 */
public record AuditId(String value) {
    public AuditId {
        AssertUtils.domainPropNotBlank("audit id must not be blank", value);
    }
}
