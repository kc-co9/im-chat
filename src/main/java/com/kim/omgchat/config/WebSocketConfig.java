package com.kim.omgchat.config;

import com.kim.omgchat.component.WebSocketNotificationServer;
import com.kim.omgchat.component.WebSocketChatServer;
import com.kim.omgchat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * <p>
 * 开启WebSocket支持
 * </p>
 *
 * @author kim
 * @since 2019/6/5 12:23
 */
@Configuration
public class WebSocketConfig {
    /**
     * ServerEndpointExporter 用于扫描和注册所有携带 ServerEndPoint 注解的实例，
     * 若部署到外部容器 则无需提供此类。
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {

        return new ServerEndpointExporter();
    }

    /**
     * 因 SpringBoot WebSocket 对每个客户端连接都会创建一个 WebSocketServer（@ServerEndpoint 注解对应的） 对象，
     * Bean 注入操作会被直接略过，因而手动注入一个全局变量
     *
     * @param userService
     */
    @Autowired
    public void setMessageService(UserService userService, StringRedisTemplate redisTemplate) {
        WebSocketChatServer.userService = userService;
        WebSocketChatServer.redisTemplate = redisTemplate;

        WebSocketNotificationServer.userService = userService;
        WebSocketNotificationServer.redisTemplate = redisTemplate;
    }
}
