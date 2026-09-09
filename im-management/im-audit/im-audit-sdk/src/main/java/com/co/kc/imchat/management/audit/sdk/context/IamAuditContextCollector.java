package com.co.kc.imchat.management.audit.sdk.context;

import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditClientContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityContext;
import com.co.kc.imchat.management.iam.sdk.security.model.IamPrincipal;
import com.co.kc.imchat.plugin.web.context.HttpRequestContext;
import com.co.kc.imchat.plugin.web.context.HttpRequestContextHolder;
import com.co.kc.imchat.plugin.web.logging.LoggingUtils;

/** 在 IAM SDK 可用时收集管理应用的管理员审计主体。 */
public class IamAuditContextCollector implements AuditContextCollector {
    private static final String NO_TRACE = "N/A";

    @Override
    public AuditContext collect() {
        AuditActor actor = IamSecurityContext.currentPrincipal()
                .map(IamAuditContextCollector::actor)
                .orElseGet(() -> new DefaultAuditContextCollector().collect().actor());
        AuditClientContext client = HttpRequestContextHolder.get()
                .map(IamAuditContextCollector::client)
                .orElse(null);
        String traceId = LoggingUtils.getTraceId();
        return new AuditContext(actor, client, NO_TRACE.equals(traceId) ? null : traceId);
    }

    private static AuditActor actor(IamPrincipal principal) {
        return new AuditActor(
                "ADMINISTRATOR",
                String.valueOf(principal.administratorId()),
                principal.username());
    }

    private static AuditClientContext client(HttpRequestContext context) {
        return new AuditClientContext(context.clientAddress(), context.userAgent());
    }
}
