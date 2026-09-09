package com.co.kc.imchat.management.audit.model.io;

import java.util.Map;

/** 审计扩展属性请求。 */
public record AuditAttributesRequest(
        /* 显式声明的扩展属性。 */
        Map<String, String> values
) {
}
