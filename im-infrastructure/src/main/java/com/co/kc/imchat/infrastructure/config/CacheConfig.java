package com.co.kc.imchat.infrastructure.config;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.infrastructure.model.SessionDTO;
import com.co.kc.imchat.infrastructure.support.constant.CacheNames;
import com.co.kc.imchat.infrastructure.support.constant.RedisKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<Long, User> userIdCache(CacheManager cacheManager) {
        return remoteCache(cacheManager, CacheNames.USER_ID, Duration.of(30, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<String, User> userEmailCache(CacheManager cacheManager) {
        return remoteCache(cacheManager, CacheNames.USER_EMAIL, Duration.of(30, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<String, Boolean> userEmailContainCache(CacheManager cacheManager) {
        return remoteCache(cacheManager, CacheNames.USER_EMAIL_CONTAIN, Duration.of(10, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<Long, Group> groupIdCache(CacheManager cacheManager) {
        return remoteCache(cacheManager, CacheNames.GROUP_ID, Duration.of(10, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<String, GroupMember> groupMemberCache(CacheManager cacheManager) {
        return remoteCache(cacheManager, CacheNames.GROUP_MEMBER, Duration.of(5, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<String, Friend> friendEdgeCache(CacheManager cacheManager) {
        return remoteCache(cacheManager, CacheNames.FRIEND_EDGE, Duration.of(5, ChronoUnit.MINUTES));
    }

    @Bean
    public Cache<Long, SessionDTO> userSessionCache(CacheManager cacheManager) {
        return remoteCache(cacheManager, RedisKey.IM_CHAT_SESSION.getValue(), Duration.of(1, ChronoUnit.DAYS));
    }

    private <K, V> Cache<K, V> remoteCache(CacheManager cacheManager, String name, Duration expire) {
        QuickConfig config =
                QuickConfig.newBuilder(name)
                        .expire(expire)
                        .cacheType(CacheType.REMOTE)
                        .build();
        return cacheManager.getOrCreateCache(config);
    }

}
