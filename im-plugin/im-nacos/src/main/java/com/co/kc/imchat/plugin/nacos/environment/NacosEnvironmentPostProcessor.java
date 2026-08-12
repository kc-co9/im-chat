package com.co.kc.imchat.plugin.nacos.environment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

/**
 * 在配置数据处理前加载 im-nacos 提供的 Nacos 默认配置。
 */
public final class NacosEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "im-nacos";
    private static final String CONFIG_LOCATION = "META-INF/config/im-nacos.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                    .load(PROPERTY_SOURCE_NAME, new ClassPathResource(CONFIG_LOCATION));
            propertySources.forEach(environment.getPropertySources()::addLast);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load " + CONFIG_LOCATION, ex);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }
}
