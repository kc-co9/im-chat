package com.co.kc.imchat.plugin.cache.core;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.template.QuickConfig;
import com.co.kc.imchat.plugin.cache.model.CacheSpec;

import java.time.Duration;
import java.util.Objects;

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
