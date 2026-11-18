package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.application.frame.FrameDispatchAppService;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.gateway.GatewayDispatchAdapter;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
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
    public GatewayDispatchAdapter gatewayDispatchAdapter(
            GatewayClient gatewayClient,
            @Qualifier("brokerFrameDispatchExecutor") ExecutorService executor,
            MetricsCollector metricsCollector) {
        return new GatewayDispatchAdapter(gatewayClient, executor, metricsCollector);
    }

    @Bean
    public FrameDispatchAppService frameDispatchAppService(
            ConnectionRegistry connectionRegistry,
            GatewayRegistry gatewayRegistry,
            BrokerRoutingProperties routingProperties,
            BrokerRaftAdapter brokerClusterAdapter,
            BrokerPeerAdapter brokerPeerAdapter,
            GatewayDispatchAdapter gatewayDispatchAdapter,
            RegistryProperties registryProperties,
            JetCacheFactory cacheFactory,
            @Qualifier("brokerFrameDispatchExecutor") ExecutorService frameDispatchExecutor) {
        return new FrameDispatchAppService(connectionRegistry, gatewayRegistry,
                brokerClusterAdapter, brokerPeerAdapter, gatewayDispatchAdapter,
                registryProperties.getGatewayTtl(),
                routingProperties.getRouteLookupNegativeCache(),
                cacheFactory.local("broker.route.negative", routingProperties.getRouteLookupNegativeCache().isZero()
                                ? Duration.ofMillis(1) : routingProperties.getRouteLookupNegativeCache(),
                        routingProperties.getRouteLookupNegativeCacheCapacity()));
    }
}
