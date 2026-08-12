package com.co.kc.imchat.plugin.datasource;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.co.kc.imchat.plugin.datasource.properties.ImShardingSphereProperties;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitAspect;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

@AutoConfiguration
@EnableConfigurationProperties(ImShardingSphereProperties.class)
public class ImDatasourceAutoConfiguration {

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
                                               ResourceLoader resourceLoader) {
        Assert.hasText(properties.getConfigLocation(), "im.datasource.sharding.config-location must not be blank");
        Resource resource = resourceLoader.getResource(properties.getConfigLocation());
        if (!resource.exists()) {
            throw new BeanCreationException("ShardingSphere config not found: " + properties.getConfigLocation());
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return YamlShardingSphereDataSourceFactory.createDataSource(inputStream.readAllBytes());
        } catch (SQLException | IOException ex) {
            throw new BeanCreationException("Failed to create ShardingSphere DataSource", ex);
        }
    }
}
