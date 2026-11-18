package com.co.kc.imchat.management.ops.support.audit;

import com.co.kc.imchat.management.audit.sdk.context.AuditThreadContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/** 将已认证 CLI 请求的可信调用方放入当前线程的审计上下文。 */
public class OpsCliAuditContextFilter extends OncePerRequestFilter {
    private final OpsCliAuditContextCollector collector;

    public OpsCliAuditContextFilter(OpsCliAuditContextCollector collector) {
        this.collector = collector;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try (AuditThreadContextHolder.Scope ignored = AuditThreadContextHolder.open(
                Map.of("clientId", collector.currentClientId()))) {
            filterChain.doFilter(request, response);
        }
    }
}
