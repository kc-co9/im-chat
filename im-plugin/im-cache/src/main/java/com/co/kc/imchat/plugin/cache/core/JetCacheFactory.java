package com.co.kc.imchat.plugin.cache.core;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.template.QuickConfig;
import com.alicp.jetcache.anno.CacheType;
import com.co.kc.imchat.plugin.cache.model.CacheSpec;

import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.HashSet;

public class JetCacheFactory {

    private final CacheManager cacheManager;

    public JetCacheFactory(CacheManager cacheManager) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager must not be null");
    }

    public <K, V> Cache<K, V> remote(String name, Duration expire) {
        return create(CacheSpec.remote(name, expire));
    }

    public <K, V> Cache<K, V> local(String name, Duration expire) {
        return create(CacheSpec.local(name, expire));
    }

    /**
     * 创建支持有效值快照的有界本地缓存，容量和写入期限交给 JetCache/Caffeine 管理。
     * 使用原始键以支持快照枚举，不适用于远程或多级缓存；键必须具有稳定 equals/hashCode。
     */
    public <K, V> Cache<K, V> local(String name, Duration expire, int capacity) {
        CacheSpec spec = CacheSpec.local(name, expire);
        if (capacity <= 0) {
            throw new IllegalArgumentException("local cache capacity must be positive");
        }
        QuickConfig config = QuickConfig.newBuilder(spec.area(), spec.name())
                .cacheType(CacheType.LOCAL).expire(spec.expire()).localLimit(capacity)
                .keyConvertor(key -> key).build();
        return cacheManager.getOrCreateCache(config);
    }

    /**
     * 读取由三参数 local 创建的本地缓存有效值快照，不维护额外键索引。
     * JetCache 通用接口不提供遍历；只从其 Caffeine 代理枚举原始键，值仍通过 JetCache
     * getAll 读取以遵循过期规则。不可用于远程、多级或自定义键转换缓存。
     */
    public static <K, V> List<V> localValues(Cache<K, V> cache) {
        com.github.benmanes.caffeine.cache.Cache<K, ?> local =
                cache.unwrap(com.github.benmanes.caffeine.cache.Cache.class);
        local.cleanUp();
        return List.copyOf(cache.getAll(new HashSet<>(local.asMap().keySet())).values());
    }

    public <K, V> Cache<K, V> both(String name, Duration expire) {
        return create(CacheSpec.both(name, expire));
    }

    public <K, V> Cache<K, V> create(CacheSpec spec) {
        Objects.requireNonNull(spec, "cache spec must not be null");
        QuickConfig config =
                QuickConfig.newBuilder(spec.area(), spec.name())
                        .cacheType(spec.cacheType())
                        .expire(spec.expire())
                        .build();
        return cacheManager.getOrCreateCache(config);
    }
}
