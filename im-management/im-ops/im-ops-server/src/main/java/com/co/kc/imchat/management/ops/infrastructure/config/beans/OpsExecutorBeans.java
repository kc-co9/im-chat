package com.co.kc.imchat.management.ops.infrastructure.config.beans;

import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsQueryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Ops 多节点查询执行器配置。
 */
@Configuration
@EnableConfigurationProperties(OpsQueryProperties.class)
public class OpsExecutorBeans {

    @Bean(name = "opsQueryExecutor", destroyMethod = "shutdown")
    public ExecutorService opsQueryExecutor(OpsQueryProperties properties) {
        return new ThreadPoolExecutor(
                properties.threads(),
                properties.threads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                Thread.ofPlatform().name("ops-query-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
