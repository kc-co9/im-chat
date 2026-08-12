package com.co.kc.imchat.plugin.cache.environment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CacheEnvironmentPostProcessorTest {

    @Test
    void loadsDefaultRemoteCacheBuilder() {
        StandardEnvironment environment = new StandardEnvironment();

        new CacheEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("jetcache.remote.default.type")).isEqualTo("redisson");
        assertThat(environment.getProperty("jetcache.local.default.type")).isEqualTo("caffeine");
    }

    @Test
    void keepsExternalConfigurationAtHigherPriority() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("external",
                Map.of("jetcache.remote.default.keyPrefix", "custom")));

        new CacheEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("jetcache.remote.default.keyPrefix")).isEqualTo("custom");
    }
}
