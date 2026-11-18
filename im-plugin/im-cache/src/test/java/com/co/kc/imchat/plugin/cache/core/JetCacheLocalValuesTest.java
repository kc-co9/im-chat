package com.co.kc.imchat.plugin.cache.core;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.embedded.CaffeineCacheBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class JetCacheLocalValuesTest {
    @Test
    void snapshotUsesJetCacheExpirationAndReturnsImmutableCurrentValues() {
        Cache<String, String> cache = CaffeineCacheBuilder.createCaffeineCacheBuilder()
                .limit(2).expireAfterWrite(60, TimeUnit.SECONDS).keyConvertor(key -> key).buildCache();
        try {
            cache.put("a", "first");
            assertThat(JetCacheFactory.localValues(cache)).containsExactly("first");
            assertThatThrownBy(() -> JetCacheFactory.localValues(cache).clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            cache.put("a", "updated");
            assertThat(JetCacheFactory.localValues(cache)).containsExactly("updated");

            cache.put("expired", "excluded", -1, TimeUnit.MILLISECONDS);
            assertThat(JetCacheFactory.localValues(cache)).doesNotContain("excluded");
        } finally {
            cache.close();
        }
    }

    @Test
    void snapshotConvergesToFrameworkCapacityAfterMaintenance() {
        Cache<String, String> cache = CaffeineCacheBuilder.createCaffeineCacheBuilder()
                .limit(2).expireAfterWrite(60, TimeUnit.SECONDS).keyConvertor(key -> key).buildCache();
        try {
            for (int index = 0; index < 100; index++) {
                cache.put("key-" + index, "value-" + index);
            }
            assertThat(JetCacheFactory.localValues(cache)).isNotEmpty();
            // 一次 cleanUp 不保证排队维护已全部完成；只观察同一轮写入后的容量收敛。
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                List<String> values = JetCacheFactory.localValues(cache);
                assertThat(values).isNotEmpty().hasSizeLessThanOrEqualTo(2);
            });
        } finally {
            cache.close();
        }
    }
}
