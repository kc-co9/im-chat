package com.co.kc.imchat.gateway.http.security.authentication;

import com.co.kc.imchat.plugin.session.context.UserContextHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * HTTP 网关认证成功处理器。
 * <p>
 * 认证成功后写入可信用户上下文头，供后端 HTTP 服务读取。
 */
public class AuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        if (!(authentication instanceof AuthenticationToken tokenAuthentication)) {
            return webFilterExchange.getChain().filter(webFilterExchange.getExchange());
        }
        ServerWebExchange exchange = webFilterExchange.getExchange();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(UserContextHeaders.USER_ID);
                    headers.remove(UserContextHeaders.SESSION_VERSION);
                    headers.set(UserContextHeaders.USER_ID, String.valueOf(tokenAuthentication.getUserId()));
                    headers.set(UserContextHeaders.SESSION_VERSION, tokenAuthentication.getSessionVersion());
                })
                .build();
        return webFilterExchange.getChain().filter(exchange.mutate().request(request).build());
    }
}
