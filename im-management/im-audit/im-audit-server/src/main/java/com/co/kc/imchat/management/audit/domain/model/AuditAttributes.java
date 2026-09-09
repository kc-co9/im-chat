package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Map;

/** 显式提供且不可变的审计扩展属性。 */
public record AuditAttributes(Map<String, String> values) {
    public AuditAttributes {
        AssertUtils.domainPropNotNull("audit attributes must not be null", values);
        values.forEach((key, value) -> {
            AssertUtils.domainPropNotBlank("audit attribute key must not be blank", key);
            AssertUtils.domainPropNotNull("audit attribute value must not be null", value);
        });
        values = Map.copyOf(values);
    }
}
