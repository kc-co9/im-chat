package com.co.kc.imchat.plugin.datasource;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.co.kc.imchat.plugin.datasource.properties.ImShardingSphereProperties;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitAspect;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.SQLException;

@AutoConfiguration
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(ImShardingSphereProperties.class)
public class ImDatasourceAutoConfiguration {
    private static final Pattern SPRING_SHARDING_PLACEHOLDER = Pattern.compile(
            "\\$\\{(im\\.datasource\\.sharding\\.[^}:]+)(?::([^}]*))?}");

    /**
     * 使 MyBatis-Plus JSON TypeHandler 复用应用统一的 Jackson 配置。
     *
     * @param objectMapper 应用 Jackson Mapper
     * @return TypeHandler 初始化动作
     */
    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    public InitializingBean mybatisJsonObjectMapper(ObjectMapper objectMapper) {
        return () -> JacksonTypeHandler.setObjectMapper(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public AfterTransactionCommitTemplate afterTransactionCommitTemplate() {
        return new AfterTransactionCommitTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public AfterTransactionCommitAspect afterTransactionCommitAspect(AfterTransactionCommitTemplate template) {
        return new AfterTransactionCommitAspect(template);
    }

    @Bean
    @ConditionalOnClass(YamlShardingSphereDataSourceFactory.class)
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(prefix = "im.datasource.sharding", name = "enabled", havingValue = "true")
    public DataSource shardingSphereDataSource(ImShardingSphereProperties properties,
                                               ResourceLoader resourceLoader,
                                               Environment environment) {
        Assert.hasText(properties.getConfigLocation(), "im.datasource.sharding.config-location must not be blank");
        Resource resource = resourceLoader.getResource(properties.getConfigLocation());
        if (!resource.exists()) {
            throw new BeanCreationException("ShardingSphere config not found: " + properties.getConfigLocation());
        }
        try {
            try (InputStream inputStream = resource.getInputStream()) {
                String yaml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                String resolvedYaml = resolveSpringShardingPlaceholders(yaml, environment);
                return YamlShardingSphereDataSourceFactory.createDataSource(
                        resolvedYaml.getBytes(StandardCharsets.UTF_8));
            }
        } catch (SQLException | IOException ex) {
            throw new BeanCreationException("Failed to create ShardingSphere DataSource", ex);
        }
    }

    /**
     * ShardingSphere Factory 直接读取 YAML，不会经过 Spring Environment。
     * 这里只解析应用自有的 {@code im.datasource.sharding.*}，必须保留
     * ShardingSphere 使用的 {@code ${0..7}}、{@code ${user_id % 8}} 等 inline 表达式。
     */
    static String resolveSpringShardingPlaceholders(String yaml, Environment environment) {
        Matcher matcher = SPRING_SHARDING_PLACEHOLDER.matcher(yaml);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(resolved,
                    Matcher.quoteReplacement(environment.resolveRequiredPlaceholders(matcher.group())));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
