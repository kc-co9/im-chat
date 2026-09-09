package com.co.kc.imchat.management.audit.domain.model;

/** 审计事实关联的非安全客户端上下文。 */
public record AuditClientContext(String address, String userAgent) {
}
