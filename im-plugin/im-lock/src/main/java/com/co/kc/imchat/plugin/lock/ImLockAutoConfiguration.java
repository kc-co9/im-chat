package com.co.kc.imchat.plugin.lock;

import com.co.kc.imchat.plugin.lock.aspect.DistributeLockAspect;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.lock.core.RedissonLockClient;
import com.co.kc.imchat.plugin.lock.spi.LockClient;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(RedissonAutoConfigurationV2.class)
@ConditionalOnBean(RedissonClient.class)
public class ImLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LockClient.class)
    public RedissonLockClient redissonLockClient(RedissonClient redissonClient) {
        return new RedissonLockClient(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLockTemplate distributedLockTemplate(LockClient lockClient) {
        return new DistributedLockTemplate(lockClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributeLockAspect distributeLockAspect(DistributedLockTemplate distributedLockTemplate) {
        return new DistributeLockAspect(distributedLockTemplate);
    }
}
