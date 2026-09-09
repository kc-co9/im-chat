package com.co.kc.imchat.management.audit.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 按业务 auditId 查询审计详情。 */
public record AuditDetailsQuery(String auditId) {
    public AuditDetailsQuery {
        AssertUtils.argNotBlank("audit id must not be blank", auditId);
    }
}
