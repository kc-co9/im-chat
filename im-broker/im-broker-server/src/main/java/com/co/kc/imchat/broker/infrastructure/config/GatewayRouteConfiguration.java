package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.broker.application.local.LocalRouteAppService;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.connection.repository.BatchConnectionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



/** Gateway 路由写入授权和快照装配。 */
@Configuration
public class GatewayRouteConfiguration {

    @Bean
    public LocalRouteAppService localRouteAppService(
            BrokerRaftProperties raftProperties,
            BrokerRaftAdapter repository,
            LocalRoutingLeaseService leaseManager,
            BatchConnectionRegistry connectionRegistry,
            BrokerRoutingProperties routingProperties,
            DomainEventPublisher eventPublisher) {
        return new LocalRouteAppService(raftProperties.getNodeId(), repository,
                leaseManager, connectionRegistry, eventPublisher, routingProperties.getSnapshotBatchTimeout());
    }

}
