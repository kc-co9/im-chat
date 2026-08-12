package com.co.kc.imchat.plugin.cache.environment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

/**
 * 在配置数据处理前加载 im-cache 提供的缓存默认配置。
 */
public final class CacheEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String CONFIG_LOCATION = "META-INF/config/im-cache.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            new YamlPropertySourceLoader().load("im-cache", new ClassPathResource(CONFIG_LOCATION))
                    .forEach(environment.getPropertySources()::addLast);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load " + CONFIG_LOCATION, ex);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }
}
