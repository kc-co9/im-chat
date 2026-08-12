package com.co.kc.imchat.gateway.http.security.authentication;

import com.co.kc.imchat.common.constant.HttpHeaderConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * HTTP 网关认证转换器。
 * <p>
 * 从请求头中提取客户端 token，并转换为未认证的 Spring Security Authentication。
 */
public class AuthenticationConverter implements ServerAuthenticationConverter {

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String token = resolveToken(exchange.getRequest());
        if (StringUtils.isBlank(token)) {
            return Mono.empty();
        }
        return Mono.just(new AuthenticationToken(token));
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.startsWith(authorization, HttpHeaderConstants.BEARER_PREFIX)) {
            return authorization.substring(HttpHeaderConstants.BEARER_PREFIX.length()).trim();
        }
        return request.getHeaders().getFirst(HttpHeaderConstants.TOKEN);
    }
}
