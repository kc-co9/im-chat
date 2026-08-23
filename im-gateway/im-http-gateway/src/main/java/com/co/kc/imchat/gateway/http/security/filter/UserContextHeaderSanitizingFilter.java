package com.co.kc.imchat.gateway.http.security.filter;

import com.co.kc.imchat.plugin.session.context.UserContextHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 用户上下文头清理过滤器。
 * <p>
 * 外部请求不能直接携带内部用户上下文，避免绕过网关伪造身份。
 */
public class UserContextHeaderSanitizingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(UserContextHeaders.USER_ID);
                    headers.remove(UserContextHeaders.SESSION_VERSION);
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
