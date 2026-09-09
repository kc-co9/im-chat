package com.co.kc.imchat.management.audit.model.io;

/** 审计事件客户端元数据请求。 */
public record AuditClientRequest(
        /* 客户端网络地址，可选。 */
        String address,
        /* User-Agent，可选。 */
        String userAgent
) {
}
