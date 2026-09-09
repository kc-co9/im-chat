package com.co.kc.imchat.management.audit.sdk.transport;

/** 审计事件投递方式。 */
public enum AuditTransportType {
    /** Spring Cloud Stream Kafka Binder。 */
    KAFKA,
    /** IAM 机器身份认证的异步 HTTP。 */
    HTTP
}
