package com.co.kc.imchat.infrastructure.config;

import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.support.web.interceptor.WsContextInterceptor;
import com.co.kc.imchat.support.web.interceptor.WsHandshakeInterceptor;
import com.co.kc.imchat.infrastructure.support.WsHandshakeHandler;
import com.co.kc.imchat.support.auth.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TokenService tokenService;
    private final UserAppService userAppService;
    
    /**
     * 配置TaskScheduler，用于处理WebSocket心跳任务
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setThreadNamePrefix("websocket-heartbeat-");
        taskScheduler.setPoolSize(1);
        taskScheduler.setRemoveOnCancelPolicy(true);
        return taskScheduler;
    }

    /**
     * 1. 注册STOMP端点：客户端实际连接的入口
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/web") // 连接地址：ws://localhost:8080/web
                .addInterceptors(new WsHandshakeInterceptor(tokenService, userAppService))
                .setHandshakeHandler(new WsHandshakeHandler())
                .setAllowedOriginPatterns("*"); // 允许跨域，在Spring Boot 2.4+中应该使用allowedOriginPatterns而不是allowedOrigins
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WsContextInterceptor(userAppService)); // <--- 注册在这里
    }

    /**
     * 2. 配置消息代理：处理消息路由
     * <p>
     * STOMP 心跳机制配置
     *
     * <p><strong>注意：</strong> 此配置<strong>并非</strong>设置 Session 的“有效期”或“过期时间”。
     * 它仅用于网络层的<strong>活性检测（保活）</strong>。
     *
     * <p><strong>工作原理：</strong>
     * <ul>
     *   <li><strong>服务端 -> 客户端 (outbound):</strong> 服务端每隔 N 毫秒发送心跳包，检测客户端是否在线。</li>
     *   <li><strong>客户端 -> 服务端 (inbound):</strong> 客户端必须至少每隔 N 毫秒发送心跳或数据。
     *       如果服务端超过超时阈值（通常是设置值的 3 倍）未收到客户端响应，会强制关闭底层连接。</li>
     * </ul>
     *
     * <p><strong>业务有效期控制：</strong>
     * 真正的“用户会话有效期”（如 5 分钟无操作自动下线）应由业务逻辑控制，
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 配置应用前缀，以该前缀开头的消息会被@MessageMapping注解的方法处理
        registry.setApplicationDestinationPrefixes("/chat");

        // 启用简单内存代理，以/topic或/queue开头的目的地支持广播或点对点
        registry.enableSimpleBroker("/topic", "/queue")
                // ⭐ 设置心跳 ⭐
                // 参数：[ 服务端发送心跳的间隔(毫秒), 客户端必须在多少毫秒内发送心跳 ]
                .setHeartbeatValue(new long[]{30000, 30000})
                // 设置TaskScheduler用于处理心跳任务
                .setTaskScheduler(taskScheduler());
    }
}
