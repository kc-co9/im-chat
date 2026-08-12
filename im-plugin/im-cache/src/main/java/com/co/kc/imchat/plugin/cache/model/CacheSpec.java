package com.co.kc.imchat.plugin.cache.model;

import com.alicp.jetcache.anno.CacheType;

import java.time.Duration;
import java.util.Objects;

public record CacheSpec(String area, String name, CacheType cacheType, Duration expire) {

    public static final String DEFAULT_AREA = "default";

    public CacheSpec {
        if (area == null || area.isBlank()) {
            area = DEFAULT_AREA;
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("cache name must not be blank");
        }
        Objects.requireNonNull(cacheType, "cache type must not be null");
        Objects.requireNonNull(expire, "cache expire must not be null");
        if (expire.isZero() || expire.isNegative()) {
            throw new IllegalArgumentException("cache expire must be positive");
        }
    }

    public static CacheSpec remote(String name, Duration expire) {
        return remote(DEFAULT_AREA, name, expire);
    }

    public static CacheSpec remote(String area, String name, Duration expire) {
        return new CacheSpec(area, name, CacheType.REMOTE, expire);
    }

    public static CacheSpec local(String name, Duration expire) {
        return local(DEFAULT_AREA, name, expire);
    }

    public static CacheSpec local(String area, String name, Duration expire) {
        return new CacheSpec(area, name, CacheType.LOCAL, expire);
    }

    public static CacheSpec both(String name, Duration expire) {
        return both(DEFAULT_AREA, name, expire);
    }

    public static CacheSpec both(String area, String name, Duration expire) {
        return new CacheSpec(area, name, CacheType.BOTH, expire);
    }
}
