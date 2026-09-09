package com.co.kc.imchat.management.audit.sdk.transport;

import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;

/** 审计事件的单一已选投递通道。 */
@FunctionalInterface
public interface AuditTransport {

    /**
     * 将事件交给当前投递通道。
     *
     * @param event 审计事件
     */
    void send(AuditEvent event);
}
