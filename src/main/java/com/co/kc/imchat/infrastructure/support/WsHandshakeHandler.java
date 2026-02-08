package com.co.kc.imchat.infrastructure.support;

import com.co.kc.imchat.model.enums.ParamsConstants;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

public class WsHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(@NotNull ServerHttpRequest request,
                                      @NotNull WebSocketHandler wsHandler,
                                      @NotNull Map<String, Object> attributes) {
        return () -> attributes.get(ParamsConstants.USER_ID).toString();
    }
}
