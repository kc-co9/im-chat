package com.kim.omgchat.infrastructure.interceptor;

import com.kim.omgchat.application.UserAppService;
import com.kim.omgchat.model.cqrs.dto.user.TokenDTO;
import com.kim.omgchat.model.cqrs.query.user.UserAuthQuery;
import com.kim.omgchat.support.user.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    private final TokenService tokenService;
    private final UserAppService userAppService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest serverRequest,
                                   ServerHttpResponse serverResponse,
                                   WebSocketHandler webSocketHandler,
                                   Map<String, Object> attributes) {
        try {
            // 1. 从请求参数或 Header 中获取 Token
            // 例如：ws://localhost:8080/ws?token=xxxxx
            String token = serverResponse.getHeaders().getFirst("token");
            if (StringUtils.isBlank(token)) {
                log.info("WebSocket认证失败，缺少token参数");
                return false;
            }

            TokenDTO tokenDTO = tokenService.parse(token);

            boolean isAuthenticated = userAppService.isAuthenticated(new UserAuthQuery(tokenDTO.getUserId()));
            if (!isAuthenticated) {
                log.info("WebSocket认证失败，用户未登录");
                return false;
            }

            // 这个 attributes 会传递给 WebSocket 的 Session
            attributes.put("userId", tokenDTO.getUserId());

            return true;
        } catch (Exception ex) {
            log.warn("WebSocket认证失败: {}", ex.getMessage());
            return false; // 拒绝连接
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse, WebSocketHandler webSocketHandler, Exception e) {

    }
}
