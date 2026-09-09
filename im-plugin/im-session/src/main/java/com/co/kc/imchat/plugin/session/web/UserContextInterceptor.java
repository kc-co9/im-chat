package com.co.kc.imchat.plugin.session.web;

import com.co.kc.imchat.plugin.session.context.UserContext;
import com.co.kc.imchat.plugin.session.context.UserContextHeaders;
import com.co.kc.imchat.plugin.session.context.UserContextUtils;
import com.co.kc.imchat.plugin.session.properties.SessionWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/** 将可信网关 Header 适配为请求范围内的用户上下文。 */
public class UserContextInterceptor implements HandlerInterceptor {
    private static final List<String> TECHNICAL_PUBLIC_PATHS = List.of(
            "/favicon.ico",
            "/doc.html",
            "/swagger-ui.html",
            "/actuator/**",
            "/webjars/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> publicPaths;

    public UserContextInterceptor(SessionWebProperties properties) {
        this.publicPaths = List.copyOf(properties.getPublicPaths());
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (isPublicRequest(request)) {
            return true;
        }

        String userId = request.getHeader(UserContextHeaders.USER_ID);
        String sessionVersion = request.getHeader(UserContextHeaders.SESSION_VERSION);
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(sessionVersion)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        try {
            Long parsedUserId = Long.valueOf(userId);
            UserContextUtils.set(new UserContext(parsedUserId, null, null, sessionVersion));
            return true;
        } catch (NumberFormatException exception) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        UserContextUtils.remove();
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        if (StringUtils.isBlank(path)) {
            path = request.getRequestURI();
        }
        return matches(TECHNICAL_PUBLIC_PATHS, path) || matches(publicPaths, path);
    }

    private boolean matches(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
