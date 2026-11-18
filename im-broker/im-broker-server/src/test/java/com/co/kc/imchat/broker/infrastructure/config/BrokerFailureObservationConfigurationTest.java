package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureObservationScheduler;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureProbeScheduler;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BrokerFailureObservationConfigurationTest {
    @Test
    void completeObservationWiringExistsWithoutTlsAndOwnsBoundedExecutor() {
        new ApplicationContextRunner().withUserConfiguration(BrokerFailureObservationConfiguration.class)
                .withBean(BrokerFailureObservationProperties.class)
                .withBean(BrokerRaftProperties.class, () -> {
                    BrokerRaftProperties properties = new BrokerRaftProperties();
                    properties.setNodeId("broker-a");
                    return properties;
                })
                .withBean(BrokerRaftAdapter.class, () -> mock(BrokerRaftAdapter.class))
                .withBean(BrokerClusterAppService.class, () -> mock(BrokerClusterAppService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(BrokerHealthObservationTracker.class)
                            .hasSingleBean(BrokerFailureObservationService.class)
                            .hasSingleBean(BrokerFailureProbeScheduler.class).hasSingleBean(BrokerFailureObservationScheduler.class);
                    ThreadPoolExecutor executor = context.getBean("brokerObservationExecutor", ThreadPoolExecutor.class);
                    assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
                    assertThat(executor.getQueue().remainingCapacity()).isEqualTo(64);
                    assertThat(executor.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
                });
    }
}
