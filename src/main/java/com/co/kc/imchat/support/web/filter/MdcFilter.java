package com.co.kc.imchat.support.web.filter;

import com.co.kc.imchat.support.utils.GeneratorUtils;
import com.co.kc.imchat.model.enums.ParamsConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author kc
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws IOException, ServletException {
        try {
            // 生成或提取 traceId
            String traceId = request.getHeader(ParamsConstants.HEADER_TRACE_ID);
            if (StringUtils.isBlank(traceId)) {
                traceId = GeneratorUtils.nextUUID();
            }

            MDC.put(ParamsConstants.TRACE_ID, traceId);
            // 可扩展更多信息，如 userId、IP 等
            MDC.put(ParamsConstants.IP, request.getRemoteAddr());

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // 避免内存泄漏
        }
    }
}
