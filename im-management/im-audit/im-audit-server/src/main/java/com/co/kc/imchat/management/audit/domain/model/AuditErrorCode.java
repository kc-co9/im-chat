package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 审计失败的稳定错误码。 */
public record AuditErrorCode(String value) {
    public AuditErrorCode {
        AssertUtils.domainPropNotBlank("audit error code must not be blank", value);
    }
}
