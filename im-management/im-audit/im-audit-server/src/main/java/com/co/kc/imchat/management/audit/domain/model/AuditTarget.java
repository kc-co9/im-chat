package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 审计事实关联的业务目标。 */
public record AuditTarget(String type, String id) {
    public AuditTarget {
        AssertUtils.domainPropNotBlank("audit target type must not be blank", type);
    }
}
