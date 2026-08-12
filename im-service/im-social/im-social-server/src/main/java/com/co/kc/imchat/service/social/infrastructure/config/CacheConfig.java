package com.co.kc.imchat.service.social.infrastructure.config;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.infrastructure.support.constant.SocialCacheNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 社交缓存配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "im.social.provider", name = "enabled", havingValue = "true")
public class CacheConfig {

    @Bean
    public Cache<Long, Group> groupIdCache(JetCacheFactory cacheFactory) {
        return remoteCache(cacheFactory, SocialCacheNames.GROUP_ID, Duration.of(10, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<String, GroupMember> groupMemberCache(JetCacheFactory cacheFactory) {
        return remoteCache(cacheFactory, SocialCacheNames.GROUP_MEMBER, Duration.of(5, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<String, Friend> friendEdgeCache(JetCacheFactory cacheFactory) {
        return remoteCache(cacheFactory, SocialCacheNames.FRIEND_EDGE, Duration.of(5, ChronoUnit.MINUTES));
    }

    private <K, V> Cache<K, V> remoteCache(JetCacheFactory cacheFactory, String name, Duration expire) {
        return cacheFactory.remote(name, expire);
    }
}
