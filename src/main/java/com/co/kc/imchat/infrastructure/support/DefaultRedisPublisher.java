package com.co.kc.imchat.infrastructure.support;

import com.co.kc.imchat.model.enums.RedisTopic;
import com.co.kc.imchat.support.redis.RedisPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultRedisPublisher implements RedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 广播消息到指定频道
     */
    @Override
    public void publish(RedisTopic topic, Object message) {
        redisTemplate.convertAndSend(topic.getValue(), message);
    }

}
