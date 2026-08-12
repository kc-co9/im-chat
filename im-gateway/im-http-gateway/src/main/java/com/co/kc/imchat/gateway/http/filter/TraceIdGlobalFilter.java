package com.co.kc.imchat.gateway.http.filter;

import com.co.kc.imchat.common.constant.HttpHeaderConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * HTTP 网关 TraceId 全局过滤器。
 * <p>
 * 负责补齐请求链路标识，并把同一个 TraceId 写回响应头，便于跨服务排查。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HttpHeaderConstants.TRACE_ID);
        ServerWebExchange tracedExchange = exchange;
        if (traceId == null || traceId.isBlank()) {
            // 外部未传 TraceId 时由网关生成，后续服务统一从请求头透传。
            traceId = UUID.randomUUID().toString();
            String generatedTraceId = traceId;
            tracedExchange = exchange.mutate()
                    .request(builder -> builder.header(HttpHeaderConstants.TRACE_ID, generatedTraceId))
                    .build();
        }

        String responseTraceId = traceId;
        ServerWebExchange responseExchange = tracedExchange;
        responseExchange.getResponse().beforeCommit(() -> {
            // 保证响应也带上 TraceId，客户端和日志系统可以用同一个值串联链路。
            if (!responseExchange.getResponse().getHeaders().containsKey(HttpHeaderConstants.TRACE_ID)) {
                responseExchange.getResponse().getHeaders().add(HttpHeaderConstants.TRACE_ID, responseTraceId);
            }
            return Mono.empty();
        });
        return chain.filter(tracedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
