package com.co.kc.imchat.plugin.cache.core;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.embedded.CaffeineCacheBuilder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JetCacheLocalValuesTest {
    @Test
    void snapshotUsesJetCacheExpirationAndFrameworkCapacity() {
        Cache<String, String> cache = CaffeineCacheBuilder.createCaffeineCacheBuilder()
                .limit(2).expireAfterWrite(60, TimeUnit.SECONDS).keyConvertor(key -> key).buildCache();
        cache.put("a", "first");
        assertThat(JetCacheFactory.localValues(cache)).containsExactly("first");
        assertThatThrownBy(() -> JetCacheFactory.localValues(cache).clear()).isInstanceOf(UnsupportedOperationException.class);
        cache.put("a", "updated");
        assertThat(JetCacheFactory.localValues(cache)).containsExactly("updated");

        cache.put("expired", "excluded", -1, TimeUnit.MILLISECONDS);
        assertThat(JetCacheFactory.localValues(cache)).doesNotContain("excluded");
        for (int index = 0; index < 100; index++) {
            cache.put("key-" + index, "value-" + index);
        }
        assertThat(JetCacheFactory.localValues(cache)).hasSizeLessThanOrEqualTo(2);
        cache.close();
    }
}
