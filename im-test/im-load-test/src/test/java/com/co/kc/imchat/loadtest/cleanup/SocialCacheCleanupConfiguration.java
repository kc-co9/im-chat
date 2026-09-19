package com.co.kc.imchat.loadtest.cleanup;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

final class SocialCacheCleanupConfiguration {

    private SocialCacheCleanupConfiguration() {
    }

    static AnnotationConfigApplicationContext open(LoadTestCleanupConfig.RedisTarget target) {
        return AccountCacheCleanupConfiguration.openOwner(target, "im-social");
    }
}
