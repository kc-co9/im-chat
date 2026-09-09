package com.co.kc.imchat.management.audit.support.security;

import com.co.kc.imchat.management.iam.sdk.security.IamApplicationPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 线程上下文读取当前审计客户端。
 */
public final class AuditClientContext {
    private AuditClientContext() {
    }

    public static IamApplicationPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IamApplicationPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "IAM machine identity is required");
        }
        return principal;
    }
}
