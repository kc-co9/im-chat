package com.co.kc.imchat.service.message.infrastructure.config;

import com.co.kc.imchat.common.utils.JsonUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * 配置RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        
        // 设置Key的序列化器
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        
        // 设置Value的序列化器
        RedisSerializer<Object> messageSerializer = redisMessageSerializer();
        redisTemplate.setValueSerializer(messageSerializer);
        redisTemplate.setHashValueSerializer(messageSerializer);
        
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    RedisSerializer<Object> redisMessageSerializer() {
        return new Jackson2JsonRedisSerializer<>(JsonUtils.getMapper(), Object.class);
    }
}
