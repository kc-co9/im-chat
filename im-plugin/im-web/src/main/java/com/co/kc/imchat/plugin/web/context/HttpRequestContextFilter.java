package com.co.kc.imchat.plugin.web.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 在 Servlet Filter 链入口建立并清理当前 HTTP 请求的非安全元数据上下文。 */
public class HttpRequestContextFilter extends OncePerRequestFilter {
    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final String UNKNOWN = "unknown";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpRequestContextHolder.set(new HttpRequestContext(
                value(request.getRemoteAddr()),
                userAgent(request)));
        try {
            filterChain.doFilter(request, response);
        } finally {
            HttpRequestContextHolder.clear();
        }
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = value(request.getHeader("User-Agent"));
        return userAgent.substring(0, Math.min(userAgent.length(), MAX_USER_AGENT_LENGTH));
    }

    private String value(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
