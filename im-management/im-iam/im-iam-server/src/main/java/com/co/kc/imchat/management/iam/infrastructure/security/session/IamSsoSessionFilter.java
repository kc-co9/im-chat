package com.co.kc.imchat.management.iam.infrastructure.security.session;

import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamAuthorizationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;

/**
 * 对 IAM 浏览器 SSO 会话同时执行空闲与绝对有效期限制。
 */
public class IamSsoSessionFilter extends OncePerRequestFilter {
    private static final String CREATED_AT = IamSsoSessionFilter.class.getName() + ".createdAt";

    private final IamAuthorizationProperties properties;

    public IamSsoSessionFilter(IamAuthorizationProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Instant now = Instant.now();
            Instant createdAt = createdAt(session);
            if (!createdAt.plus(properties.ssoAbsoluteTimeout()).isAfter(now)) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            } else {
                session.setMaxInactiveInterval(
                        Math.toIntExact(properties.ssoIdleTimeout().toSeconds()));
            }
        }
        filterChain.doFilter(request, response);
    }

    private Instant createdAt(HttpSession session) {
        Object value = session.getAttribute(CREATED_AT);
        if (value instanceof Instant instant) {
            return instant;
        }
        Instant createdAt = Instant.ofEpochMilli(session.getCreationTime());
        session.setAttribute(CREATED_AT, createdAt);
        return createdAt;
    }
}
