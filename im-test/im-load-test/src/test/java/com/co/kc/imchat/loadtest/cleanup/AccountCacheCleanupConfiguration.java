package com.co.kc.imchat.loadtest.cleanup;

import com.alicp.jetcache.autoconfigure.JetCacheAutoConfiguration;
import com.co.kc.imchat.plugin.cache.ImCacheAutoConfiguration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
class AccountCacheCleanupConfiguration {

    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(LoadTestCleanupConfig.RedisTarget target) {
        Config config = new Config();
        String password = new String(target.password());
        SingleServerConfig server = config.useSingleServer().setAddress(target.url());
        if (!password.isEmpty()) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }

    static AnnotationConfigApplicationContext open(LoadTestCleanupConfig.RedisTarget target) {
        return openOwner(target, "im-account");
    }

    static AnnotationConfigApplicationContext openOwner(LoadTestCleanupConfig.RedisTarget target, String appName) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("loadTestCacheCleanup", properties(appName)));
        context.registerBean(LoadTestCleanupConfig.RedisTarget.class, () -> target);
        context.register(AccountCacheCleanupConfiguration.class, JetCacheAutoConfiguration.class,
                com.alicp.jetcache.autoconfigure.RedissonAutoConfiguration.class,
                ImCacheAutoConfiguration.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException exception) {
            context.close();
            throw exception;
        }
    }

    private static Map<String, Object> properties(String appName) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.application.name", appName);
        properties.put("jetcache.statIntervalMinutes", "0");
        properties.put("jetcache.areaInCacheName", "false");
        properties.put("jetcache.remote.default.type", "redisson");
        properties.put("jetcache.remote.default.keyConvertor", "jackson");
        properties.put("jetcache.remote.default.broadcastChannel", appName);
        properties.put("jetcache.remote.default.keyPrefix", appName);
        properties.put("jetcache.remote.default.valueEncoder", "java");
        properties.put("jetcache.remote.default.valueDecoder", "java");
        properties.put("jetcache.remote.default.defaultExpireInMillis", "5000");
        return properties;
    }
}
