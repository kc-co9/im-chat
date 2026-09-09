package com.co.kc.imchat.management.audit.sdk.model;

import java.io.Serializable;

/** 与审计事件关联的非安全客户端元数据。 */
public record AuditClientContext(
        /* 客户端网络地址，可选。 */
        String address,
        /* User-Agent，可选。 */
        String userAgent
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AuditClientContext {
        AuditContract.validateOptional(
                "audit client address",
                address,
                AuditContract.CLIENT_ADDRESS_LENGTH);
        AuditContract.validateOptional(
                "audit user agent",
                userAgent,
                AuditContract.USER_AGENT_LENGTH);
    }
}
