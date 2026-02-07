package com.co.kc.imchat.support.web.interceptor;

import com.co.kc.imchat.support.exception.AuthException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.Map;

@RequiredArgsConstructor
public class WsContextInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        // 1. 包装消息获取 Accessor
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);

        // 2. 从 Session 属性中获取 UserId (这是在 HandshakeInterceptor 中存入的)
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Long userId = sessionAttributes != null ? (Long) sessionAttributes.get("userId") : null;
        if (userId == null) {
            throw new AuthException("用户未登录");
        }

        return message;
    }


}
