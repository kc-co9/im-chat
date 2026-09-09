package com.co.kc.imchat.management.audit.sdk.context;

import com.co.kc.imchat.management.audit.sdk.model.AuditContext;

/** 收集当前调用线程中的审计身份、请求元数据与调用链信息。 */
public interface AuditContextCollector {

    /**
     * 收集当前审计上下文。
     *
     * @return 已完成脱敏的上下文
     */
    AuditContext collect();
}
