package com.co.kc.imchat.management.audit.sdk.transport;

/** 审计投递通道无法接受或完成事件时抛出的内部异常。 */
public class AuditTransportException extends RuntimeException {

    public AuditTransportException(String message) {
        super(message);
    }

    public AuditTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
