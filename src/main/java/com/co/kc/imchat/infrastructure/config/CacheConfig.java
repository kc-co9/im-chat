package com.co.kc.imchat.infrastructure.config;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.co.kc.imchat.model.cqrs.dto.user.SessionDTO;
import com.co.kc.imchat.support.constant.RedisKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<Long, SessionDTO> userSessionCache(CacheManager cacheManager) {
        QuickConfig config =
                QuickConfig.newBuilder(RedisKey.IM_CHAT_SESSION.getValue())
                        .expire(Duration.of(1, ChronoUnit.DAYS))
                        .cacheType(CacheType.REMOTE)
                        .build();
        return cacheManager.getOrCreateCache(config);
    }
}
