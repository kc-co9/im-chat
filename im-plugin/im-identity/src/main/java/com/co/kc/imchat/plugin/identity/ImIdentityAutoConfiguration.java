package com.co.kc.imchat.plugin.identity;

import com.co.kc.imchat.plugin.identity.properties.SnowflakeProperties;
import com.co.kc.imchat.plugin.identity.snowflake.ISnowflakeMachineId;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.plugin.identity.snowflake.impl.RedisSnowflakeMachineId;
import com.co.kc.imchat.plugin.identity.snowflake.impl.StaticSnowflakeMachineId;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Snowflake ID 与 Redis 机器租约自动配置。 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfigurationV2")
@EnableConfigurationProperties(SnowflakeProperties.class)
public class ImIdentityAutoConfiguration {

    @Bean
    @ConditionalOnBean(ISnowflakeMachineId.class)
    @ConditionalOnMissingBean
    public SnowflakeId snowflakeId(ISnowflakeMachineId machineId) {
        return new SnowflakeId(machineId);
    }

    /** 静态 Snowflake 机器 ID 配置。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "im.identity.snowflake",
            name = "mode",
            havingValue = "STATIC")
    static class StaticSnowflakeConfiguration {

        @Bean
        @ConditionalOnMissingBean({ISnowflakeMachineId.class, SnowflakeId.class})
        StaticSnowflakeMachineId staticSnowflakeMachineId(SnowflakeProperties properties) {
            return new StaticSnowflakeMachineId(
                    properties.dataCenterId(),
                    properties.machineId());
        }
    }

    /** Redis Snowflake 机器租约配置。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "im.identity.snowflake",
            name = "mode",
            havingValue = "REDIS")
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    static class RedisSnowflakeConfiguration {

        @Bean(initMethod = "start", destroyMethod = "close")
        @ConditionalOnMissingBean({ISnowflakeMachineId.class, SnowflakeId.class})
        RedisSnowflakeMachineId redisSnowflakeMachineId(
                RedissonClient redissonClient,
                SnowflakeProperties properties,
                Environment environment
        ) {
            String applicationName = environment.getProperty("spring.application.name");
            return new RedisSnowflakeMachineId(
                    redissonClient,
                    properties.dataCenterId(),
                    properties.resolveNamespace(applicationName),
                    properties.leaseDuration(),
                    properties.heartbeatInterval());
        }
    }

    /** Redis 模式缺少客户端依赖时提供明确的启动失败。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "im.identity.snowflake",
            name = "mode",
            havingValue = "REDIS")
    @ConditionalOnMissingClass("org.redisson.api.RedissonClient")
    static class MissingRedisConfiguration {

        @Bean
        static BeanFactoryPostProcessor missingRedissonClient() {
            return beanFactory -> {
                throw new IllegalStateException(
                        "REDIS Snowflake mode requires RedissonClient on the classpath");
            };
        }
    }
}
