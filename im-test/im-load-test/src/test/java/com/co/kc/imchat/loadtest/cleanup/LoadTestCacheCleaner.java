package com.co.kc.imchat.loadtest.cleanup;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheGetResult;
import com.alicp.jetcache.CacheResult;
import com.alicp.jetcache.CacheResultCode;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import com.co.kc.imchat.service.account.infrastructure.support.constant.AccountCacheNames;
import com.co.kc.imchat.service.social.infrastructure.support.constant.SocialCacheNames;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class LoadTestCacheCleaner {

    private static final Duration CLEANUP_CACHE_TTL = Duration.ofMinutes(5);
    private static final String MESSAGE_PRESENCE_PREFIX = "im:message:presence:chat:";

    void cleanAccount(LoadTestBatchManifest manifest, JetCacheFactory factory) {
        clean(accountTargets(manifest), factory);
    }

    void cleanSocial(LoadTestBatchManifest manifest, JetCacheFactory factory) {
        clean(socialTargets(manifest), factory);
    }

    void cleanMessage(LoadTestBatchManifest manifest, RedissonClient redissonClient) {
        for (LoadTestBatchManifest.User user : manifest.users()) {
            RBucket<Object> bucket = redissonClient.getBucket(MESSAGE_PRESENCE_PREFIX + user.userId());
            bucket.delete();
            if (bucket.isExists()) {
                throw new IllegalStateException("message cache absence verification failed");
            }
        }
    }

    static List<Target> accountTargets(LoadTestBatchManifest manifest) {
        List<Target> targets = new ArrayList<>();
        for (LoadTestBatchManifest.User user : manifest.users()) {
            Long userId = Long.valueOf(user.userId());
            targets.add(new Target(Family.USER_ID, userId));
            targets.add(new Target(Family.USER_EMAIL, user.email()));
            targets.add(new Target(Family.USER_EMAIL_CONTAIN, user.email()));
            targets.add(new Target(Family.USER_SESSION, userId));
        }
        return List.copyOf(targets);
    }

    static List<Target> socialTargets(LoadTestBatchManifest manifest) {
        List<Target> targets = new ArrayList<>();
        for (LoadTestBatchManifest.Group group : manifest.groups()) {
            Long groupId = Long.valueOf(group.groupId());
            targets.add(new Target(Family.GROUP_ID, groupId));
            targets.add(new Target(Family.GROUP_MEMBERS, groupId));
            for (String userId : group.memberUserIds()) {
                String memberKey = group.groupId() + ":" + userId;
                targets.add(new Target(Family.GROUP_MEMBER, memberKey));
                targets.add(new Target(Family.GROUP_MEMBER_CONTAIN, memberKey));
            }
        }
        for (LoadTestBatchManifest.User user : manifest.users()) {
            targets.add(new Target(Family.FRIENDS_USER, Long.valueOf(user.userId())));
        }
        for (LoadTestBatchManifest.Relation relation : manifest.relations()) {
            String edgeKey = relation.userId() + ":" + relation.friendUserId();
            targets.add(new Target(Family.FRIEND_EDGE, edgeKey));
            targets.add(new Target(Family.FRIEND_ACTIVE, edgeKey));
        }
        return List.copyOf(targets);
    }

    private void clean(List<Target> targets, JetCacheFactory factory) {
        for (Target target : targets) {
            Cache<Object, Object> cache = factory.remote(target.family().cacheName(), CLEANUP_CACHE_TTL);
            CacheResult removal = cache.REMOVE(target.key());
            CacheGetResult<Object> readback = cache.GET(target.key());
            if (readback.getResultCode() != CacheResultCode.NOT_EXISTS
                    && readback.getResultCode() != CacheResultCode.EXPIRED) {
                if (!removal.isSuccess() && readback.getResultCode() == CacheResultCode.SUCCESS) {
                    throw new IllegalStateException("cache removal failed");
                }
                throw new IllegalStateException("cache absence verification failed");
            }
        }
    }

    record Target(Family family, Object key) {
    }

    enum Family {
        USER_ID(AccountCacheNames.USER_ID),
        USER_EMAIL(AccountCacheNames.USER_EMAIL),
        USER_EMAIL_CONTAIN(AccountCacheNames.USER_EMAIL_CONTAIN),
        USER_SESSION(AccountCacheNames.USER_SESSION),
        GROUP_ID(SocialCacheNames.GROUP_ID),
        GROUP_MEMBERS(SocialCacheNames.GROUP_MEMBERS),
        GROUP_MEMBER(SocialCacheNames.GROUP_MEMBER),
        GROUP_MEMBER_CONTAIN(SocialCacheNames.GROUP_MEMBER_CONTAIN),
        FRIENDS_USER(SocialCacheNames.FRIENDS_USER),
        FRIEND_EDGE(SocialCacheNames.FRIEND_EDGE),
        FRIEND_ACTIVE(SocialCacheNames.FRIEND_ACTIVE);

        private final String cacheName;

        Family(String cacheName) {
            this.cacheName = cacheName;
        }

        String cacheName() {
            return cacheName;
        }
    }
}
