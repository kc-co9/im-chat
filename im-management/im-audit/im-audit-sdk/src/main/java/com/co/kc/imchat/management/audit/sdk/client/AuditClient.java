package com.co.kc.imchat.management.audit.sdk.client;

import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;

/**
 * 审计事件提交入口。
 *
 * <p>方法返回只表示 SDK 已接受事件，不表示审计服务已经完成持久化。</p>
 */
public interface AuditClient {

    /**
     * 提交审计事件进行异步投递。
     *
     * @param event 完整审计事件
     */
    void submit(AuditEvent event);
}
