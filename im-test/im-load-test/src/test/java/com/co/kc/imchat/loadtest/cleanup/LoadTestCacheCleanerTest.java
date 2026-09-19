package com.co.kc.imchat.loadtest.cleanup;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheGetResult;
import com.alicp.jetcache.CacheResult;
import com.alicp.jetcache.CacheResultCode;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;

import static com.co.kc.imchat.loadtest.cleanup.LoadTestCacheCleaner.Family.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LoadTestCacheCleanerTest {

    private final LoadTestBatchManifest manifest = LoadTestBatchManifest.parse(LoadTestBatchManifestTest.validJson());
    private final LoadTestCacheCleaner cleaner = new LoadTestCacheCleaner();

    @Test
    void derivesOnlyD37KeysWithProductionKeyTypesAndDirectedRelationships() {
        assertEquals(List.of(
                new LoadTestCacheCleaner.Target(USER_ID, 101L),
                new LoadTestCacheCleaner.Target(USER_EMAIL, "loadtest-20260914-t3b1-user-0001@example.test"),
                new LoadTestCacheCleaner.Target(USER_EMAIL_CONTAIN, "loadtest-20260914-t3b1-user-0001@example.test"),
                new LoadTestCacheCleaner.Target(USER_SESSION, 101L),
                new LoadTestCacheCleaner.Target(USER_ID, 102L),
                new LoadTestCacheCleaner.Target(USER_EMAIL, "loadtest-20260914-t3b1-user-0002@example.test"),
                new LoadTestCacheCleaner.Target(USER_EMAIL_CONTAIN, "loadtest-20260914-t3b1-user-0002@example.test"),
                new LoadTestCacheCleaner.Target(USER_SESSION, 102L)), LoadTestCacheCleaner.accountTargets(manifest));
        assertEquals(List.of(
                new LoadTestCacheCleaner.Target(GROUP_ID, 201L),
                new LoadTestCacheCleaner.Target(GROUP_MEMBERS, 201L),
                new LoadTestCacheCleaner.Target(GROUP_MEMBER, "201:101"),
                new LoadTestCacheCleaner.Target(GROUP_MEMBER_CONTAIN, "201:101"),
                new LoadTestCacheCleaner.Target(GROUP_MEMBER, "201:102"),
                new LoadTestCacheCleaner.Target(GROUP_MEMBER_CONTAIN, "201:102"),
                new LoadTestCacheCleaner.Target(FRIENDS_USER, 101L),
                new LoadTestCacheCleaner.Target(FRIENDS_USER, 102L),
                new LoadTestCacheCleaner.Target(FRIEND_EDGE, "101:102"),
                new LoadTestCacheCleaner.Target(FRIEND_ACTIVE, "101:102"),
                new LoadTestCacheCleaner.Target(FRIEND_EDGE, "102:101"),
                new LoadTestCacheCleaner.Target(FRIEND_ACTIVE, "102:101")), LoadTestCacheCleaner.socialTargets(manifest));
    }

    @Test
    void removesAndVerifiesEveryAccountKeyOnRepeatedPasses() {
        JetCacheFactory factory = mock(JetCacheFactory.class);
        for (LoadTestCacheCleaner.Target target : LoadTestCacheCleaner.accountTargets(manifest)) {
            Cache<Object, Object> cache = cache(factory, target.family());
            when(cache.REMOVE(target.key())).thenReturn(CacheResult.SUCCESS_WITHOUT_MSG);
            when(cache.GET(target.key())).thenReturn(absent());
        }

        cleaner.cleanAccount(manifest, factory);
        cleaner.cleanAccount(manifest, factory);

        for (LoadTestCacheCleaner.Target target : LoadTestCacheCleaner.accountTargets(manifest)) {
            Cache<Object, Object> cache = factory.remote(target.family().cacheName(), Duration.ofMinutes(5));
            verify(cache, times(2)).REMOVE(target.key());
            verify(cache, times(2)).GET(target.key());
        }
    }

    @Test
    void removalOrReadFailureCannotBeMistakenForAbsence() {
        JetCacheFactory factory = mock(JetCacheFactory.class);
        Cache<Object, Object> cache = cache(factory, USER_ID);
        when(cache.REMOVE(101L)).thenReturn(CacheResult.FAIL_WITHOUT_MSG);
        when(cache.GET(101L)).thenReturn(new CacheGetResult<>(CacheResultCode.SUCCESS, null, null));
        assertThrows(IllegalStateException.class, () -> cleaner.cleanAccount(manifest, factory));

        when(cache.REMOVE(101L)).thenReturn(CacheResult.SUCCESS_WITHOUT_MSG);
        when(cache.GET(101L)).thenReturn(new CacheGetResult<>(CacheResultCode.FAIL, "secret", null));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> cleaner.cleanAccount(manifest, factory));
        assertEquals("cache absence verification failed", failure.getMessage());
    }

    @Test
    void absenceReadbackMakesRepeatedRemovalIdempotentDespiteFalseRemovalResult() {
        JetCacheFactory factory = mock(JetCacheFactory.class);
        for (LoadTestCacheCleaner.Target target : LoadTestCacheCleaner.accountTargets(manifest)) {
            Cache<Object, Object> cache = cache(factory, target.family());
            when(cache.REMOVE(target.key())).thenReturn(CacheResult.FAIL_WITHOUT_MSG);
            when(cache.GET(target.key())).thenReturn(absent());
        }

        cleaner.cleanAccount(manifest, factory);
        cleaner.cleanAccount(manifest, factory);

        for (LoadTestCacheCleaner.Target target : LoadTestCacheCleaner.accountTargets(manifest)) {
            Cache<Object, Object> cache = factory.remote(target.family().cacheName(), Duration.ofMinutes(5));
            verify(cache, times(2)).GET(target.key());
        }
    }

    @Test
    void messageCleanupUsesOnlyExactPresenceBucketsAndRejectsRemainingKeys() {
        RedissonClient redis = mock(RedissonClient.class);
        RBucket<Object> first = mock(RBucket.class);
        RBucket<Object> second = mock(RBucket.class);
        when(redis.getBucket("im:message:presence:chat:101")).thenReturn(first);
        when(redis.getBucket("im:message:presence:chat:102")).thenReturn(second);
        cleaner.cleanMessage(manifest, redis);
        verify(first).delete();
        verify(first).isExists();
        verify(second).delete();
        verify(second).isExists();
        verifyNoMoreInteractions(first, second);
        verify(redis).getBucket("im:message:presence:chat:101");
        verify(redis).getBucket("im:message:presence:chat:102");
        verifyNoMoreInteractions(redis);

        when(first.isExists()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> cleaner.cleanMessage(manifest, redis));
    }

    private static Cache<Object, Object> cache(JetCacheFactory factory, LoadTestCacheCleaner.Family family) {
        Cache<Object, Object> existing = factory.remote(family.cacheName(), Duration.ofMinutes(5));
        if (existing != null) {
            return existing;
        }
        Cache<Object, Object> cache = mock(Cache.class);
        when(factory.<Object, Object>remote(family.cacheName(), Duration.ofMinutes(5))).thenReturn(cache);
        return cache;
    }

    @SuppressWarnings("unchecked")
    private static CacheGetResult<Object> absent() {
        return CacheGetResult.NOT_EXISTS_WITHOUT_MSG;
    }
}
