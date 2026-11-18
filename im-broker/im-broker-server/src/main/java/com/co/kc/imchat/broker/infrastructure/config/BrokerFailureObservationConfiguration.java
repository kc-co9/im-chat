package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureObservationScheduler;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureProbeScheduler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 观察资源和预算装配；队列饱和安全拒绝，不占用 fence/Lease 执行器。
 */
@Configuration
public class BrokerFailureObservationConfiguration {
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor brokerObservationExecutor(BrokerFailureObservationProperties properties) {
        properties.validate();
        return new ThreadPoolExecutor(properties.getMaxConcurrentRequests(), properties.getMaxConcurrentRequests(),
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(properties.getMaxPendingRequests()),
                Thread.ofPlatform().daemon().name("broker-observation-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public BrokerHealthObservationTracker brokerHealthObservationTracker(BrokerRaftProperties raft,
                                                                    BrokerFailureObservationProperties properties) {
        properties.validate();
        return new BrokerHealthObservationTracker(new BrokerId(raft.getNodeId()), properties.getFreshness());
    }

    @Bean
    public BrokerFailureObservationService brokerFailureObservationService(BrokerFailureObservationProperties properties) {
        properties.validate();
        return new BrokerFailureObservationService(properties.getStableWindow(), properties.getFreshness());
    }

    @Bean
    public BrokerFailureProbeScheduler brokerFailureProbeScheduler(BrokerClusterAppService app) {
        return new BrokerFailureProbeScheduler(app);
    }

    @Bean
    public BrokerFailureObservationScheduler brokerFailureObservationScheduler(BrokerClusterAppService app) {
        return new BrokerFailureObservationScheduler(app);
    }
}
