package com.co.kc.imchat.loadtest.cleanup;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoadTestCacheBootstrapTest {

    @Test
    void disposableRedisProvesRealJetCacheNamespacesExactCleanupAndResourceClosure() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:7.2.0"))
                .withExposedPorts(6379);
        try (container) {
            container.start();
            assertTrue(container.isRunning());
            assertNotEquals(6379, container.getMappedPort(6379));
            String endpoint = "redis://127.0.0.1:" + container.getMappedPort(6379);
            LoadTestCleanupConfig.RedisTarget target = LoadTestCleanupConfig.redisTarget(endpoint, "");
            LoadTestBatchManifest manifest = LoadTestBatchManifest.parse(LoadTestBatchManifestTest.validJson());
            LoadTestCacheCleaner cleaner = new LoadTestCacheCleaner();

            seedOwner(AccountCacheCleanupConfiguration.open(target), LoadTestCacheCleaner.accountTargets(manifest));
            seedOwner(SocialCacheCleanupConfiguration.open(target), LoadTestCacheCleaner.socialTargets(manifest));

            RedissonClient accountRedis;
            try (AnnotationConfigApplicationContext account = AccountCacheCleanupConfiguration.open(target)) {
                accountRedis = account.getBean(RedissonClient.class);
                assertOwnerSettings(account, "im-account");
                JetCacheFactory factory = account.getBean(JetCacheFactory.class);
                assertEquals("account-only", factory.remote("namespace-probe:", Duration.ofMinutes(5)).get(101L));
                cleaner.cleanAccount(manifest, factory);
                cleaner.cleanAccount(manifest, factory);
                assertCleaned(factory, LoadTestCacheCleaner.accountTargets(manifest));
            }
            assertTrue(accountRedis.isShutdown());

            RedissonClient socialRedis;
            try (AnnotationConfigApplicationContext social = SocialCacheCleanupConfiguration.open(target)) {
                socialRedis = social.getBean(RedissonClient.class);
                assertOwnerSettings(social, "im-social");
                JetCacheFactory factory = social.getBean(JetCacheFactory.class);
                assertEquals("social-only", factory.remote("namespace-probe:", Duration.ofMinutes(5)).get(101L));
                cleaner.cleanSocial(manifest, factory);
                cleaner.cleanSocial(manifest, factory);
                assertCleaned(factory, LoadTestCacheCleaner.socialTargets(manifest));
            }
            assertTrue(socialRedis.isShutdown());

            RedissonClient messageRedis;
            try (AnnotationConfigApplicationContext message = MessageRedisCleanupConfiguration.open(target)) {
                messageRedis = message.getBean(RedissonClient.class);
                messageRedis.getBucket("im:message:presence:chat:101").set("301");
                messageRedis.getBucket("im:message:presence:chat:102").set("302");
                messageRedis.getBucket("im:message:presence:chat:999").set("ordinary");
                cleaner.cleanMessage(manifest, messageRedis);
                cleaner.cleanMessage(manifest, messageRedis);
                assertFalse(messageRedis.getBucket("im:message:presence:chat:101").isExists());
                assertFalse(messageRedis.getBucket("im:message:presence:chat:102").isExists());
                assertEquals("ordinary", messageRedis.getBucket("im:message:presence:chat:999").get());
            }
            assertTrue(messageRedis.isShutdown());
        } finally {
            assertFalse(container.isRunning(), "test-owned Redis must be stopped even on failure");
        }
    }

    private static void seedOwner(AnnotationConfigApplicationContext context,
            List<LoadTestCacheCleaner.Target> targets) {
        RedissonClient redis;
        try (context) {
            redis = context.getBean(RedissonClient.class);
            JetCacheFactory factory = context.getBean(JetCacheFactory.class);
            for (LoadTestCacheCleaner.Target target : targets) {
                Cache<Object, Object> cache = factory.remote(target.family().cacheName(), Duration.ofMinutes(5));
                cache.put(target.key(), "batch");
                cache.put(ordinaryKey(target.key()), "ordinary");
                assertEquals("batch", cache.get(target.key()));
            }
            String app = context.getEnvironment().getProperty("spring.application.name");
            factory.remote("namespace-probe:", Duration.ofMinutes(5)).put(101L,
                    "im-account".equals(app) ? "account-only" : "social-only");
        }
        assertTrue(redis.isShutdown());
    }

    private static void assertCleaned(JetCacheFactory factory, List<LoadTestCacheCleaner.Target> targets) {
        for (LoadTestCacheCleaner.Target target : targets) {
            Cache<Object, Object> cache = factory.remote(target.family().cacheName(), Duration.ofMinutes(5));
            assertNull(cache.get(target.key()), target.family().name());
            assertEquals("ordinary", cache.get(ordinaryKey(target.key())), target.family().name());
        }
    }

    private static Object ordinaryKey(Object key) {
        return key instanceof Long ? 999L : "ordinary:" + key;
    }

    private static void assertOwnerSettings(AnnotationConfigApplicationContext context, String app) {
        assertEquals(app, context.getEnvironment().getProperty("spring.application.name"));
        assertEquals(app, context.getEnvironment().getProperty("jetcache.remote.default.keyPrefix"));
        assertEquals("redisson", context.getEnvironment().getProperty("jetcache.remote.default.type"));
        assertEquals("jackson", context.getEnvironment().getProperty("jetcache.remote.default.keyConvertor"));
        assertEquals("java", context.getEnvironment().getProperty("jetcache.remote.default.valueEncoder"));
        assertEquals("java", context.getEnvironment().getProperty("jetcache.remote.default.valueDecoder"));
    }
}
