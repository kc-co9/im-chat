package com.co.kc.imchat.management.ops.support.audit;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.iam.sdk.security.model.IamPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从已认证的管理员 Bearer 上下文采集 CLI 来源，不改写审计主体或网络元数据。 */
@Component
public class OpsCliAuditContextCollector {
    /** 从当前安全上下文提取经过 IAM 验证的管理员 Client ID。 */
    public String currentClientId() {
        // 同时校验主体、introspection 结果及管理员身份一致性。
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IamPrincipal principal)
                || !(authentication.getDetails() instanceof IamIntrospectionResult details)
                || !details.active() || !"ADMINISTRATOR".equals(details.principalType())
                || !principal.administratorId().equals(details.administratorId())
                || details.clientId() == null || details.clientId().isBlank()) {
            throw new IllegalStateException("Trusted CLI audit identity is unavailable");
        }
        return details.clientId();
    }
}
