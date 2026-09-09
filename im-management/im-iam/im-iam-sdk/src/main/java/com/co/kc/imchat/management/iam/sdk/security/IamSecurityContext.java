package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.management.iam.sdk.security.model.IamPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** 统一读取当前请求的 IAM 主体，不引入业务线程变量。 */
public final class IamSecurityContext {
    private IamSecurityContext() {
    }

    public static Optional<IamPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IamPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }
}
