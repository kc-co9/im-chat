package com.co.kc.imchat.management.iam.support.security;

import com.co.kc.imchat.common.exception.AuthException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.stream.Collectors;

/**
 * IAM 自身管理请求的当前管理员安全上下文。
 */
public final class IamAdministratorContext {

    private IamAdministratorContext() {
    }

    public static IamAdministratorPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("IAM 管理员未认证");
        }
        try {
            return new IamAdministratorPrincipal(
                    Long.valueOf(authentication.getName()),
                    authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .collect(Collectors.toUnmodifiableSet()));
        } catch (NumberFormatException exception) {
            throw new AuthException("IAM 管理员认证无效");
        }
    }
}
