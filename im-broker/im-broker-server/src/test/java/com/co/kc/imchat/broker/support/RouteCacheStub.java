package com.co.kc.imchat.broker.support;

import com.alicp.jetcache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 测试线程上的有界 JetCache 替身；真实过期及容量策略由 im-cache 集成测试验证。 */
public final class RouteCacheStub {
    private RouteCacheStub() {
    }

    public static <K, V> Cache<K, V> local(Duration ttl, int capacity) {
        Cache<K, V> cache = mock(Cache.class);
        com.github.benmanes.caffeine.cache.Cache<K, Object> keys = Caffeine.newBuilder().build();
        Map<K, V> values = new LinkedHashMap<>();
        Map<K, Instant> expiresAt = new LinkedHashMap<>();
        doAnswer(invocation -> {
            K key = invocation.getArgument(0);
            values.put(key, invocation.getArgument(1));
            expiresAt.put(key, Instant.now().plus(ttl));
            keys.put(key, Boolean.TRUE);
            while (values.size() > capacity) {
                K oldest = values.keySet().iterator().next();
                values.remove(oldest);
                expiresAt.remove(oldest);
                keys.invalidate(oldest);
            }
            return null;
        }).when(cache).put(any(), any());
        doAnswer(invocation -> {
            K key = invocation.getArgument(0);
            values.put(key, invocation.getArgument(1));
            expiresAt.put(key, Instant.now().plusMillis(invocation.getArgument(2)));
            keys.put(key, Boolean.TRUE);
            return null;
        }).when(cache).put(any(), any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        when(cache.get(any())).thenAnswer(invocation -> {
            K key = invocation.getArgument(0);
            Instant expiry = expiresAt.get(key);
            return expiry != null && Instant.now().isBefore(expiry) ? values.get(key) : null;
        });
        doAnswer(invocation -> {
            K key = invocation.getArgument(0);
            expiresAt.remove(key);
            keys.invalidate(key);
            return values.remove(key) != null;
        }).when(cache).remove(any());
        when(cache.unwrap(com.github.benmanes.caffeine.cache.Cache.class)).thenReturn(keys);
        when(cache.getAll(any(Set.class))).thenAnswer(invocation -> {
            Map<K, V> visible = new LinkedHashMap<>();
            for (K key : (Set<K>) invocation.getArgument(0)) {
                Instant expiry = expiresAt.get(key);
                if (expiry != null && Instant.now().isBefore(expiry)) {
                    visible.put(key, values.get(key));
                }
            }
            return visible;
        });
        return cache;
    }
}
