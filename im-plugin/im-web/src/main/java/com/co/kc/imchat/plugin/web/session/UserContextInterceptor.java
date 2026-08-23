package com.co.kc.imchat.plugin.web.session;

import com.co.kc.imchat.plugin.session.context.UserContext;
import com.co.kc.imchat.plugin.session.context.UserContextHeaders;
import com.co.kc.imchat.plugin.session.context.UserContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

public class UserContextInterceptor implements HandlerInterceptor {
    private static final List<String> PUBLIC_PATHS = List.of(
            "/user/signUp",
            "/user/signIn",
            "/user/refreshToken",
            "/favicon.ico",
            "/doc.html",
            "/swagger-ui.html",
            "/v3/api-docs/swagger-config"
    );
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/actuator",
            "/webjars/",
            "/swagger-ui/",
            "/v3/api-docs/"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
        } catch (NumberFormatException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
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
        return PUBLIC_PATHS.contains(path) || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
