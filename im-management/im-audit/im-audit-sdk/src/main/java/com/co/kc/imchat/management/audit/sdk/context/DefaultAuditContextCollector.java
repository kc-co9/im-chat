package com.co.kc.imchat.management.audit.sdk.context;

import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditClientContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.plugin.web.context.HttpRequestContext;
import com.co.kc.imchat.plugin.web.context.HttpRequestContextHolder;
import com.co.kc.imchat.plugin.web.logging.LoggingUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 IAM 和通用 Web 上下文收集当前调用线程的审计元数据。 */
public class DefaultAuditContextCollector implements AuditContextCollector {
    private static final String NO_TRACE = "N/A";

    @Override
    public AuditContext collect() {
        AuditActor actor = securityActor();
        AuditClientContext client = HttpRequestContextHolder.get()
                .map(DefaultAuditContextCollector::client)
                .orElse(null);
        String traceId = LoggingUtils.getTraceId();
        return new AuditContext(actor, client, NO_TRACE.equals(traceId) ? null : traceId);
    }

    private static AuditActor securityActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new AuditActor("SYSTEM", null, "system");
        }
        return new AuditActor("AUTHENTICATED", null, authentication.getName());
    }

    private static AuditClientContext client(HttpRequestContext context) {
        return new AuditClientContext(context.clientAddress(), context.userAgent());
    }
}
