package com.co.kc.imchat.plugin.identity;

import com.co.kc.imchat.plugin.identity.snowflake.ISnowflakeMachineId;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.plugin.identity.snowflake.impl.RedisSnowflakeMachineId;
import com.co.kc.imchat.plugin.identity.snowflake.impl.StaticSnowflakeMachineId;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImIdentityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "spring.application.name=im-test",
                    "im.identity.snowflake.data-center-id=7")
            .withConfiguration(AutoConfigurations.of(ImIdentityAutoConfiguration.class));

    @Test
    void configuresLifecycleManagedRedisMachineId() {
        contextRunner.withPropertyValues("im.identity.snowflake.mode=REDIS")
                .withUserConfiguration(RedissonConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ISnowflakeMachineId.class);
                    assertThat(context).hasSingleBean(SnowflakeId.class);
                    assertThat(context.getBean(ISnowflakeMachineId.class))
                            .isInstanceOf(RedisSnowflakeMachineId.class);
                    assertThat(context.getBean(ISnowflakeMachineId.class).getMachineId())
                            .isEqualTo(0L);
                });
    }

    @Test
    void configuresStaticMachineId() {
        contextRunner.withPropertyValues(
                        "im.identity.snowflake.mode=STATIC",
                        "im.identity.snowflake.machine-id=9")
                .run(context -> {
                    assertThat(context).hasSingleBean(ISnowflakeMachineId.class);
                    assertThat(context).hasSingleBean(SnowflakeId.class);
                    assertThat(context.getBean(ISnowflakeMachineId.class))
                            .isInstanceOf(StaticSnowflakeMachineId.class);
                    assertThat(context.getBean(ISnowflakeMachineId.class).getMachineId())
                            .isEqualTo(9L);
                });
    }

    @Test
    void rejectsMissingMode() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsRedisModeWithoutRedissonClient() {
        contextRunner.withPropertyValues("im.identity.snowflake.mode=REDIS")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsRedisModeWithoutRedissonClasses() {
        contextRunner.withClassLoader(new FilteredClassLoader("org.redisson"))
                .withPropertyValues("im.identity.snowflake.mode=REDIS")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void backsOffWhenApplicationProvidesMachineId() {
        contextRunner.withPropertyValues(
                        "im.identity.snowflake.mode=STATIC",
                        "im.identity.snowflake.machine-id=8")
                .withUserConfiguration(CustomMachineIdConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ISnowflakeMachineId.class);
                    assertThat(context).hasSingleBean(SnowflakeId.class);
                    assertThat(context).doesNotHaveBean(RedisSnowflakeMachineId.class);
                    assertThat(context.getBean(ISnowflakeMachineId.class).getMachineId())
                            .isEqualTo(9L);
                });
    }

    @Test
    void backsOffRedisWhenApplicationProvidesSnowflakeId() {
        contextRunner.withUserConfiguration(
                        RedissonConfiguration.class,
                        CustomSnowflakeIdConfiguration.class)
                .withPropertyValues("im.identity.snowflake.mode=REDIS")
                .run(context -> {
                    assertThat(context).hasSingleBean(SnowflakeId.class);
                    assertThat(context).doesNotHaveBean(RedisSnowflakeMachineId.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedissonConfiguration {
        @Bean
        RedissonClient redissonClient() {
            RedissonClient redissonClient = mock(RedissonClient.class);
            RScript script = mock(RScript.class);
            when(redissonClient.getScript()).thenReturn(script);
            when(script.eval(
                    eq(RScript.Mode.READ_WRITE),
                    anyString(),
                    eq(RScript.ReturnType.BOOLEAN),
                    anyList(),
                    any(Object[].class)))
                    .thenReturn(true);
            return redissonClient;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMachineIdConfiguration {
        @Bean
        ISnowflakeMachineId snowflakeMachineId() {
            return new StaticSnowflakeMachineId(7L, 9L);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSnowflakeIdConfiguration {
        @Bean
        SnowflakeId snowflakeId() {
            return new SnowflakeId(new StaticSnowflakeMachineId(7L, 9L));
        }
    }
}
