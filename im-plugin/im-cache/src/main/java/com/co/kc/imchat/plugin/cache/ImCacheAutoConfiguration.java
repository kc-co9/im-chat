package com.co.kc.imchat.plugin.cache;

import com.alicp.jetcache.CacheManager;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(CacheManager.class)
public class ImCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JetCacheFactory jetCacheFactory(CacheManager cacheManager) {
        return new JetCacheFactory(cacheManager);
    }
}
