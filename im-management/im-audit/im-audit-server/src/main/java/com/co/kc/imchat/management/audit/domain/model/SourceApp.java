package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 产生审计事实的应用标识。 */
public record SourceApp(String value) {
    public SourceApp {
        AssertUtils.domainPropNotBlank("audit source app must not be blank", value);
    }
}
