package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 审计事实关联的调用链标识。 */
public record TraceId(String value) {
    public TraceId {
        AssertUtils.domainPropNotBlank("audit trace id must not be blank", value);
    }
}
