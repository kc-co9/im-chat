package com.kim.omgchat.infrastructure.config;

import com.kim.omgchat.application.UserAppService;
import com.kim.omgchat.domain.session.SessionRepository;
import com.kim.omgchat.infrastructure.interceptor.WsContextInterceptor;
import com.kim.omgchat.infrastructure.interceptor.WsHandshakeInterceptor;
import com.kim.omgchat.support.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TokenService tokenService;
    private final UserAppService userAppService;

    /**
     * 1. 注册STOMP端点：客户端实际连接的入口
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/web") // 连接地址：ws://localhost:8080/web
                .addInterceptors(new WsHandshakeInterceptor(tokenService, userAppService))
                .setAllowedOrigins("*"); // 允许跨域
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WsContextInterceptor(userAppService)); // <--- 注册在这里
    }

    /**
     * 2. 配置消息代理：处理消息路由
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 配置应用前缀，以该前缀开头的消息会被@MessageMapping注解的方法处理
        registry.setApplicationDestinationPrefixes("/chat");

        // 启用简单内存代理，以/topic或/queue开头的目的地支持广播或点对点
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
