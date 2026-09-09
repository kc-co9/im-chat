package com.co.kc.imchat.service.account.infrastructure.config;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import com.co.kc.imchat.plugin.session.properties.JwtProperties;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.infrastructure.support.constant.AccountCacheNames;
import com.co.kc.imchat.service.account.model.cqrs.dto.SessionDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 账号缓存配置。
 */
@Configuration
public class CacheConfig {

    @Bean
    public Cache<Long, User> userIdCache(JetCacheFactory cacheFactory) {
        return remoteCache(cacheFactory, AccountCacheNames.USER_ID, Duration.of(30, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<String, User> userEmailCache(JetCacheFactory cacheFactory) {
        return remoteCache(cacheFactory, AccountCacheNames.USER_EMAIL, Duration.of(30, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<String, Boolean> userEmailContainCache(JetCacheFactory cacheFactory) {
        return remoteCache(cacheFactory, AccountCacheNames.USER_EMAIL_CONTAIN, Duration.of(10, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<Long, SessionDTO> userSessionCache(JetCacheFactory cacheFactory,
                                                    JwtProperties jwtProperties) {
        return remoteCache(cacheFactory, AccountCacheNames.USER_SESSION, jwtProperties.getRefreshTokenTtl());
    }

    private <K, V> Cache<K, V> remoteCache(JetCacheFactory cacheFactory, String name, Duration expire) {
        return cacheFactory.remote(name, expire);
    }
}
