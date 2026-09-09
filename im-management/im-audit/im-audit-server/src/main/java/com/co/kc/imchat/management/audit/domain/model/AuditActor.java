package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 审计事实的操作者快照。 */
public record AuditActor(String type, String id, String name) {
    public AuditActor {
        AssertUtils.domainPropNotBlank("audit actor type must not be blank", type);
    }
}
