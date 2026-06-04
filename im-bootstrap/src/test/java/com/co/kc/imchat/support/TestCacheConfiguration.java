package com.co.kc.imchat.support;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.embedded.LinkedHashMapCacheBuilder;
import com.alicp.jetcache.support.BroadcastManager;
import com.alicp.jetcache.template.QuickConfig;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableStore;
import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@TestConfiguration
public class TestCacheConfiguration {

    @Bean
    @Primary
    CacheManager cacheManager() {
        return new MemoryCacheManager();
    }

    @Bean
    @Primary
    ImMessageConfirmableStore imMessageConfirmableStore() {
        return new NoOpConfirmableStore();
    }

    private static class NoOpConfirmableStore implements ImMessageConfirmableStore {
        @Override
        public void offer(ReceiptTask task) {
        }

        @Override
        public void confirm(String receiptId) {
        }

        @Override
        public void startConfirming(Consumer<ReceiptTask> consumer) {
        }

        @Override
        public void stopConfirming() {
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
