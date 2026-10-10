package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.application.frame.FrameDispatchAppService;
import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



/** 下行帧定位与 Gateway 投递装配。 */
@Configuration
public class FrameDeliveryConfiguration {

    /** 阻塞式 Gateway 写帧使用专用虚拟线程，随应用上下文关闭。 */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService brokerFrameDispatchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public FrameDispatchAppService frameDispatchAppService(
            ConnectionRegistry connectionRegistry,
            GatewayRegistry gatewayRegistry,
            BrokerRoutingProperties routingProperties,
            BrokerClusterAdapter brokerClusterAdapter,
            RegistryProperties registryProperties,
            JetCacheFactory cacheFactory,
            @Qualifier("brokerFrameDispatchExecutor") ExecutorService frameDispatchExecutor) {
        return new FrameDispatchAppService(connectionRegistry, gatewayRegistry,
                brokerClusterAdapter,
                registryProperties.getGatewayTtl(),
                routingProperties.getRouteLookupNegativeCache(),
                cacheFactory.local("broker.route.negative", routingProperties.getRouteLookupNegativeCache().isZero()
                                ? Duration.ofMillis(1) : routingProperties.getRouteLookupNegativeCache(),
                        routingProperties.getRouteLookupNegativeCacheCapacity()));
    }
}
