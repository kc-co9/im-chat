package com.co.kc.imchat.support.web.interceptor;

import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.model.cqrs.dto.user.TokenDTO;
import com.co.kc.imchat.model.cqrs.query.user.UserAuthQuery;
import com.co.kc.imchat.support.auth.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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
    public boolean beforeHandshake(@NotNull ServerHttpRequest serverRequest,
                                   @NotNull ServerHttpResponse serverResponse,
                                   @NotNull WebSocketHandler webSocketHandler,
                                   @NotNull Map<String, Object> attributes) {
        try {
            // 1. 从请求头中获取 Token
            String token = serverRequest.getHeaders().getFirst("token");

            // 2. 如果请求头中没有，尝试从查询参数中获取 Token
            // 例如：ws://localhost:8080/ws?token=xxxxx
            if (StringUtils.isBlank(token)) {
                String query = serverRequest.getURI().getQuery();
                if (StringUtils.isNotBlank(query) && query.contains("token=")) {
                    token = query.substring(query.indexOf("token=") + 6);
                    if (token.contains("&")) {
                        token = token.substring(0, token.indexOf("&"));
                    }
                }
            }
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
    public void afterHandshake(@NotNull ServerHttpRequest serverRequest,
                               @NotNull ServerHttpResponse serverResponse,
                               @NotNull WebSocketHandler webSocketHandler,
                               Exception e) {
        // TODO document why this method is empty
    }
}
