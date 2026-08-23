package com.co.kc.imchat.service.account.infrastructure.config;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import com.co.kc.imchat.plugin.session.properties.JwtProperties;
import com.co.kc.imchat.service.account.infrastructure.support.constant.AccountCacheNames;
import com.co.kc.imchat.service.account.model.cqrs.dto.SessionDTO;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheConfigTest {

    @Test
    void sessionCacheUsesRefreshTokenLifetime() {
        JetCacheFactory cacheFactory = mock(JetCacheFactory.class);
        JwtProperties jwtProperties = new JwtProperties();
        Duration refreshTokenTtl = Duration.ofDays(30);
        jwtProperties.setRefreshTokenTtl(refreshTokenTtl);
        @SuppressWarnings("unchecked")
        Cache<Long, SessionDTO> cache = mock(Cache.class);
        when(cacheFactory.<Long, SessionDTO>remote(AccountCacheNames.USER_SESSION, refreshTokenTtl))
                .thenReturn(cache);

        Cache<Long, SessionDTO> configuredCache = new CacheConfig().userSessionCache(cacheFactory, jwtProperties);

        assertThat(configuredCache).isSameAs(cache);
        verify(cacheFactory).<Long, SessionDTO>remote(AccountCacheNames.USER_SESSION, refreshTokenTtl);
    }
}
