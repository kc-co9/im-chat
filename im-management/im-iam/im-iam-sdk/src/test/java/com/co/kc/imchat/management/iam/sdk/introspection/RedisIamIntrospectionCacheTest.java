package com.co.kc.imchat.management.iam.sdk.introspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisIamIntrospectionCacheTest {

    @Test
    void cacheKeyContainsDigestAndNeverRawToken() {
        RedisIamIntrospectionCache cache = new RedisIamIntrospectionCache(
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                Duration.ofMinutes(5));

        String key = cache.key("raw-access-token", "imAdmin");

        assertThat(key).startsWith("im:iam:introspection:imAdmin:")
                .doesNotContain("raw-access-token");
        assertThat(key.substring(key.lastIndexOf(':') + 1)).hasSize(64);
    }
}
