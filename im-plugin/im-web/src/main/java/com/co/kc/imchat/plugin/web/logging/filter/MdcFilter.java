package com.co.kc.imchat.plugin.web.logging.filter;

import com.co.kc.imchat.common.constant.HttpHeaderConstants;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.plugin.web.contants.WebConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * HTTP 请求 MDC 上下文过滤器。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        try {
            String traceId = request.getHeader(HttpHeaderConstants.TRACE_ID);
            if (StringUtils.isBlank(traceId)) {
                traceId = GeneratorUtils.nextUUID();
            }

            MDC.put(WebConstants.TRACE_ID, traceId);
            MDC.put(WebConstants.IP, request.getRemoteAddr());

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
