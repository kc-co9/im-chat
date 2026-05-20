package com.co.kc.imchat.infrastructure.config;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.application.support.redis.RedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

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

    /**
     * Redis发布订阅机制-监听器配置
     */
    @Bean
    public RedisMessageListenerContainer messageListenerContainer(RedisConnectionFactory factory, List<RedisSubscriber<?>> subscribers) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        for (RedisSubscriber<?> subscriber : subscribers) {
            container.addMessageListener(newMessageListenerAdapter(subscriber), newMessageTopic(subscriber));
        }
        return container;
    }

    private MessageListenerAdapter newMessageListenerAdapter(RedisSubscriber<?> subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "onMessage");
        adapter.setSerializer(redisSerializer(subscriber));
        adapter.afterPropertiesSet();
        return adapter;
    }

    private Topic newMessageTopic(RedisSubscriber<?> subscriber) {
        return new ChannelTopic(subscriber.topic().getValue());
    }

    RedisSerializer<Object> redisMessageSerializer() {
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        jackson2JsonRedisSerializer.setObjectMapper(JsonUtils.getMapper());
        return jackson2JsonRedisSerializer;
    }

    RedisSerializer<?> redisSerializer(RedisSubscriber<?> subscriber) {
        // 1. 获取具体的泛型类型
        Class<?> messageType = resolveMessageGenericType(subscriber);
        // 2. 创建针对该类型 T 的序列化器
        Jackson2JsonRedisSerializer<?> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(messageType);
        jackson2JsonRedisSerializer.setObjectMapper(JsonUtils.getMapper());
        return jackson2JsonRedisSerializer;
    }

    private Class<?> resolveMessageGenericType(RedisSubscriber<?> subscriber) {
        Class<?> clazz = subscriber.getClass();
        // 遍历所有实现的接口
        for (Type type : clazz.getGenericInterfaces()) {
            if (type instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) type;
                // 确保是 RedisSubscriber 接口，不依赖顺序
                if (pt.getRawType() == RedisSubscriber.class) {
                    Type actualType = pt.getActualTypeArguments()[0];
                    if (actualType instanceof Class) {
                        return (Class<?>) actualType;
                    }
                }
            }
        }
        // 如果没找到，返回 Object（或者抛出异常，视业务严格程度而定）
        return Object.class;
    }
}
