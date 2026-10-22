package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.local.LocalLeaseAppService;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.domain.authority.FileFenceWatermarkRepository;
import com.co.kc.imchat.broker.interfaces.scheduler.RoutingLeaseScheduler;
import com.co.kc.imchat.broker.interfaces.handler.routing.RouteLeasePrepareHandler;
import com.co.kc.imchat.broker.interfaces.handler.routing.RoutingFenceHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routing Lease、epoch fence 和本机执行准入装配。
 */
@Configuration
public class RoutingLeaseConfiguration {

    @Bean
    public FileFenceWatermarkRepository fenceWatermarkRepository(
            BrokerRaftProperties properties) {
        return new FileFenceWatermarkRepository(
                properties.dataPath().resolve("routing-fence.json"),
                properties.getClusterId(), properties.getNodeId());
    }

    @Bean
    public LocalRoutingLeaseService localRoutingLeaseManager(
            BrokerRaftAdapter repository,
            BrokerRaftProperties properties,
            FileFenceWatermarkRepository watermarkRepository) {
        return new LocalRoutingLeaseService(
                new BrokerId(properties.getNodeId()),
                () -> new RoutingEpoch(repository.getCommittedState().epoch()), watermarkRepository);
    }

    @Bean
    public RoutingLeaseGrantService routingLeaseGrantService(BrokerRoutingProperties properties) {
        return new RoutingLeaseGrantService(properties.leaseGrantDuration());
    }

    @Bean
    public LocalLeaseAppService localLeaseAppService(
            LocalRoutingLeaseService leaseManager,
            BrokerRoutingProperties properties, JetCacheFactory cacheFactory) {
        // 缓存到期释放 prepare 容量；accept 仍按单调准备起点核对安装资格。
        return new LocalLeaseAppService(leaseManager,
                cacheFactory.local("broker.lease.pending", properties.getMaxLeaseDuration(),
                        properties.getLeasePendingCapacity()));
    }

    @Bean
    public RoutingLeaseScheduler routingLeaseLifecycle(
            BrokerRaftAdapter repository,
            BrokerClusterAppService leaseActivator) {
        return new RoutingLeaseScheduler(repository, leaseActivator);
    }

    @Bean
    public RouteLeasePrepareHandler routeLeasePrepareHandler(
            LocalLeaseAppService activationService,
            BrokerRaftProperties properties) {
        return new RouteLeasePrepareHandler(activationService,
                properties.getNodeId());
    }

    @Bean
    public RoutingFenceHandler routingFenceHandler(
            LocalRoutingLeaseService leaseManager,
            BrokerRaftProperties properties) {
        return new RoutingFenceHandler(leaseManager,
                properties.getNodeId());
    }
}
