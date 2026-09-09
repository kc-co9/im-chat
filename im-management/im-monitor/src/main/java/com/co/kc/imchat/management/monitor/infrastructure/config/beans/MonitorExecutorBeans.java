package com.co.kc.imchat.management.monitor.infrastructure.config.beans;

import com.co.kc.imchat.management.monitor.infrastructure.config.properties.MonitorQueryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Monitor 多节点查询执行器配置。
 */
@Configuration
@EnableConfigurationProperties(MonitorQueryProperties.class)
public class MonitorExecutorBeans {

    @Bean(name = "monitorQueryExecutor", destroyMethod = "shutdown")
    public ExecutorService monitorQueryExecutor(MonitorQueryProperties properties) {
        return new ThreadPoolExecutor(
                properties.threads(),
                properties.threads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                Thread.ofPlatform().name("monitor-query-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
