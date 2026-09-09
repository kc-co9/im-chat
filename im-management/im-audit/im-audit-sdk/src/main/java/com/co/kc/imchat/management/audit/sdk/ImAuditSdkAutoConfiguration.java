package com.co.kc.imchat.management.audit.sdk;

import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.client.DefaultAuditClient;
import com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.context.DefaultAuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.context.IamAuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.properties.AuditProperties;
import com.co.kc.imchat.management.audit.sdk.support.AuditEventFactory;
import com.co.kc.imchat.management.audit.sdk.support.AuditFailureReporter;
import com.co.kc.imchat.management.audit.sdk.support.AuditTemplate;
import com.co.kc.imchat.management.audit.sdk.support.AuditedAspect;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransport;
import com.co.kc.imchat.management.audit.sdk.transport.http.AuditAccessTokenProvider;
import com.co.kc.imchat.management.audit.sdk.transport.http.HttpAuditTransport;
import com.co.kc.imchat.management.audit.sdk.transport.http.IamClientCredentialsTokenProvider;
import com.co.kc.imchat.management.audit.sdk.transport.kafka.KafkaAuditTransport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 审计 SDK 的上下文、事件工厂和声明式切面自动配置。 */
@AutoConfiguration
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "im.audit", name = "enabled", havingValue = "true")
public class ImAuditSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.co.kc.imchat.management.iam.sdk.security.IamSecurityContext")
    public AuditContextCollector iamAuditContextCollector() {
        return new IamAuditContextCollector();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("com.co.kc.imchat.management.iam.sdk.security.IamSecurityContext")
    public AuditContextCollector auditContextCollector() {
        return new DefaultAuditContextCollector();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditFailureReporter auditFailureReporter(
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable(
                () -> Metrics.globalRegistry);
        return new AuditFailureReporter(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditEventFactory auditEventFactory() {
        return new AuditEventFactory(Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder")
    @ConditionalOnProperty(prefix = "im.audit", name = "transport", havingValue = "kafka")
    public AuditTransport kafkaAuditTransport(
            StreamBridge streamBridge,
            AuditProperties properties
    ) {
        return new KafkaAuditTransport(streamBridge, properties.kafka());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.audit", name = "transport", havingValue = "http")
    public AuditAccessTokenProvider auditAccessTokenProvider(
            AuditProperties properties
    ) {
        RestClient restClient = restClient(properties.iam().timeout());
        return new IamClientCredentialsTokenProvider(
                restClient,
                properties.iam(),
                Clock.systemUTC());
    }

    @Bean(name = "auditHttpExecutor", destroyMethod = "shutdownNow")
    @ConditionalOnProperty(prefix = "im.audit", name = "transport", havingValue = "http")
    public ExecutorService auditHttpExecutor(AuditProperties properties) {
        AtomicInteger threadNumber = new AtomicInteger();
        int workerThreads = properties.http().workerThreads();
        return new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.http().queueCapacity()),
                action -> new Thread(
                        action,
                        "audit-http-" + threadNumber.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.audit", name = "transport", havingValue = "http")
    public AuditTransport httpAuditTransport(
            AuditProperties properties,
            AuditAccessTokenProvider tokenProvider,
            ExecutorService auditHttpExecutor,
            AuditFailureReporter failureReporter
    ) {
        RestClient restClient = restClient(properties.http().timeout());
        return new HttpAuditTransport(
                restClient,
                tokenProvider,
                properties.http(),
                auditHttpExecutor,
                failureReporter);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditClient auditClient(
            AuditTransport transport,
            AuditFailureReporter failureReporter
    ) {
        return new DefaultAuditClient(transport, failureReporter);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditTemplate auditTemplate(
            AuditClient auditClient,
            AuditContextCollector contextCollector,
            AuditEventFactory eventFactory,
            AuditFailureReporter failureReporter
    ) {
        return new AuditTemplate(
                auditClient,
                contextCollector,
                eventFactory,
                failureReporter);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditedAspect auditedAspect(
            AuditClient auditClient,
            AuditContextCollector contextCollector,
            AuditEventFactory eventFactory,
            AuditFailureReporter failureReporter
    ) {
        return new AuditedAspect(
                auditClient,
                contextCollector,
                eventFactory,
                failureReporter);
    }

    private RestClient restClient(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
