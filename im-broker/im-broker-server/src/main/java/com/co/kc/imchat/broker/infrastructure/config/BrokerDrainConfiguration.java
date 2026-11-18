package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerDrainScheduler;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureRecoveryScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/** Broker 排空和故障恢复调度装配。 */
@Configuration
public class BrokerDrainConfiguration {

    @Bean
    public BrokerDrainScheduler brokerDrainLifecycle(
            BrokerRaftAdapter repository,
            BrokerClusterAppService service) {
        return new BrokerDrainScheduler(repository, service);
    }

    @Bean
    public BrokerFailureRecoveryScheduler brokerFailureRecoveryLifecycle(
            BrokerRaftAdapter repository,
            BrokerClusterAppService service) {
        return new BrokerFailureRecoveryScheduler(repository, service);
    }
}
