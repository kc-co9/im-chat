package com.co.kc.imchat.broker.support.context;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerManagementProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.Arrays;

/**
 * 在 Controller 进入前统一校验管理凭证并建立请求上下文。
 */
@RequiredArgsConstructor
public final class BrokerWriteAccessContextInterceptor implements AsyncHandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final BrokerManagementProperties properties;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        BrokerWriteAccess access = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), BrokerWriteAccess.class);
        if (access == null) {
            access = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), BrokerWriteAccess.class);
        }
        if (access == null) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        String audience = request.getHeader("X-Im-Audience");
        String scopes = request.getHeader("X-Im-Scopes");
        String operationId = request.getHeader("Idempotency-Key");
        String epoch = request.getHeader("If-Match-Epoch");
        if (StringUtils.isBlank(authorization) || !authorization.equals(BEARER_PREFIX + properties.getWriteToken())) {
            throw new SecurityException("Broker management service credential is not authorized");
        }
        if (StringUtils.isBlank(audience) || !audience.equals(properties.getWriteAudience())) {
            throw new SecurityException("Broker management service credential is not authorized");
        }
        if (StringUtils.isBlank(scopes) || Arrays.stream(scopes.trim().split("\\s+")).noneMatch(access.value().value()::equals)) {
            throw new SecurityException("Broker management service credential is not authorized");
        }
        if ((access.operationRequired() && StringUtils.isBlank(operationId))) {
            throw new IllegalArgumentException("Idempotency-Key and If-Match-Epoch are required");
        }
        if (StringUtils.isBlank(epoch)) {
            throw new IllegalArgumentException("Idempotency-Key and If-Match-Epoch are required");
        }
        BrokerWriteAccessContextHolder.set(new BrokerWriteAccessContext(operationId, Long.valueOf(epoch)));
        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(@NonNull HttpServletRequest request,
                                               @NonNull HttpServletResponse response,
                                               @NonNull Object handler) {
        // Controller 已捕获请求事实；初始 Servlet 线程退出时必须解除绑定。
        BrokerWriteAccessContextHolder.clear();
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) throws Exception {
        BrokerWriteAccessContextHolder.clear();
    }
}
