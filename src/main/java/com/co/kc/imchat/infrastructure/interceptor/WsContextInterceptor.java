package com.co.kc.imchat.infrastructure.interceptor;

import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.support.context.UserContext;
import com.co.kc.imchat.support.context.UserContextUtils;
import com.co.kc.imchat.model.cqrs.dto.user.UserDetailDTO;
import com.co.kc.imchat.model.cqrs.query.user.UserDetailQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.Map;

@RequiredArgsConstructor
public class WsContextInterceptor implements ChannelInterceptor {
    private final UserAppService userAppService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // 1. 包装消息获取 Accessor
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);

        // 2. 从 Session 属性中获取 UserId (这是在 HandshakeInterceptor 中存入的)
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Long userId = sessionAttributes != null ? (Long) sessionAttributes.get("userId") : null;
        if (userId == null) {
            throw new AuthException("用户未登录");
        }

        // 3. 获取用户详情
        UserDetailDTO userDetailDTO = userAppService.userDetail(new UserDetailQuery(userId));

        // 4. 存入 ThreadLocal
        UserContext webUser = new UserContext();
        webUser.setUserId(userDetailDTO.getUserId());
        webUser.setEmail(userDetailDTO.getEmail());
        webUser.setUsername(userDetailDTO.getUsername());
        UserContextUtils.set(webUser);

        return message;
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        // 3. 用完记得清理，防止内存泄漏
        UserContextUtils.remove();
    }
}
