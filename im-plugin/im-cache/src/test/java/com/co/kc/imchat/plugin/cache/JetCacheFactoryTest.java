package com.co.kc.imchat.plugin.cache;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import com.co.kc.imchat.plugin.cache.model.CacheSpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JetCacheFactoryTest {

    @Test
    void remoteCacheUsesDefaultAreaAndExpire() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache<Object, Object> cache = mock(Cache.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(cache);

        JetCacheFactory cacheFactory = new JetCacheFactory(cacheManager);

        Cache<String, String> created = cacheFactory.remote("im:test", Duration.ofMinutes(5));

        ArgumentCaptor<QuickConfig> captor = ArgumentCaptor.forClass(QuickConfig.class);
        verify(cacheManager).getOrCreateCache(captor.capture());
        QuickConfig config = captor.getValue();
        assertThat(created).isSameAs(cache);
        assertThat(config.getArea()).isEqualTo(CacheSpec.DEFAULT_AREA);
        assertThat(config.getName()).isEqualTo("im:test");
        assertThat(config.getCacheType()).isEqualTo(CacheType.REMOTE);
        assertThat(config.getExpire()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void customSpecCanSelectLocalOrBothCache() {
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));
        JetCacheFactory cacheFactory = new JetCacheFactory(cacheManager);

        cacheFactory.create(CacheSpec.local("local", "im:local", Duration.ofSeconds(30)));
        cacheFactory.create(CacheSpec.both("both", "im:both", Duration.ofMinutes(1)));

        ArgumentCaptor<QuickConfig> captor = ArgumentCaptor.forClass(QuickConfig.class);
        verify(cacheManager, org.mockito.Mockito.times(2)).getOrCreateCache(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(QuickConfig::getArea, QuickConfig::getName, QuickConfig::getCacheType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("local", "im:local", CacheType.LOCAL),
                        org.assertj.core.groups.Tuple.tuple("both", "im:both", CacheType.BOTH));
    }

    @Test
    void cacheSpecRejectsInvalidValues() {
        assertThatThrownBy(() -> CacheSpec.remote(" ", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        assertThatThrownBy(() -> CacheSpec.remote("im:test", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expire");
    }
}
