package com.co.kc.imchat.management.iam.sdk.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 统一定义管理应用 BFF 的公开前端请求与受保护请求边界。
 */
public final class IamBffRequestPolicy {
    private static final String ASSET_PATH_PREFIX = "/assets/";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/",
            "/index.html",
            "/favicon.ico",
            "/error",
            "/iam/login",
            "/iam/callback");
    private static final String[] PUBLIC_PATH_PATTERNS = {
            "/",
            "/index.html",
            "/favicon.ico",
            "/error",
            "/assets/**",
            "/iam/login",
            "/iam/callback"
    };
    private static final String[] AUTHENTICATED_PATH_PATTERNS = {
            "/iam/**",
            "/api/**",
            "/v3/api-docs/**"
    };

    private IamBffRequestPolicy() {
    }

    /**
     * 返回无需 IAM 应用会话的请求路径模式。
     */
    public static String[] publicPathPatterns() {
        return PUBLIC_PATH_PATTERNS.clone();
    }

    /**
     * 返回必须建立 IAM 应用会话的请求路径模式。
     */
    public static String[] authenticatedPathPatterns() {
        return AUTHENTICATED_PATH_PATTERNS.clone();
    }

    /**
     * 判断当前请求是否应跳过 IAM 应用会话恢复与 Introspection。
     */
    public static boolean isPublic(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return PUBLIC_PATHS.contains(requestPath)
                || requestPath.startsWith(ASSET_PATH_PREFIX);
    }
}
