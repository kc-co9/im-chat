package com.co.kc.imchat;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.embedded.LinkedHashMapCacheBuilder;
import com.alicp.jetcache.support.BroadcastManager;
import com.alicp.jetcache.template.QuickConfig;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
        "im.message.delay.worker.enabled=false"
})
public class ImChatApplicationTests {

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        CacheManager cacheManager() {
            return new MemoryCacheManager();
        }
    }

    private static class MemoryCacheManager implements CacheManager {
        private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> Cache<K, V> getCache(String area, String cacheName) {
            return (Cache<K, V>) caches.get(cacheKey(area, cacheName));
        }

        @Override
        public void putCache(String area, String cacheName, Cache cache) {
            caches.put(cacheKey(area, cacheName), cache);
        }

        @Override
        public BroadcastManager getBroadcastManager(String area) {
            return null;
        }

        @Override
        public void putBroadcastManager(String area, BroadcastManager broadcastManager) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> Cache<K, V> getOrCreateCache(QuickConfig config) {
            return (Cache<K, V>) caches.computeIfAbsent(
                    cacheKey(config.getArea(), config.getName()),
                    key -> LinkedHashMapCacheBuilder.createLinkedHashMapCacheBuilder().limit(100).buildCache());
        }

        private String cacheKey(String area, String cacheName) {
            return area + ":" + cacheName;
        }
    }
}
