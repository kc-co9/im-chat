package com.co.kc.imchat.management.iam.support.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 读取当前请求已认证的 OAuth 客户端身份。 */
public final class IamClientContext {
    private IamClientContext() {
    }

    /**
     * 获取当前 OAuth 客户端标识。
     *
     * @return 客户端标识
     */
    public static String clientId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "OAuth client authentication is required");
        }
        return authentication.getName();
    }
}
