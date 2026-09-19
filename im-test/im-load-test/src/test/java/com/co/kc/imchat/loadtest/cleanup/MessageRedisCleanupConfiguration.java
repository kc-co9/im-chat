package com.co.kc.imchat.loadtest.cleanup;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

final class MessageRedisCleanupConfiguration {

    private MessageRedisCleanupConfiguration() {
    }

    static AnnotationConfigApplicationContext open(LoadTestCleanupConfig.RedisTarget target) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(RedissonClient.class, () -> createClient(target), bean ->
                bean.setDestroyMethodName("shutdown"));
        try {
            context.refresh();
            return context;
        } catch (RuntimeException exception) {
            context.close();
            throw exception;
        }
    }

    private static RedissonClient createClient(LoadTestCleanupConfig.RedisTarget target) {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer().setAddress(target.url());
        String password = new String(target.password());
        if (!password.isEmpty()) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
